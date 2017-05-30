/**
 * 
 */
package com.sf.vas.mtnvtu.tools;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sf.vas.atjpa.entities.CurrentCycleInfo;
import com.sf.vas.atjpa.entities.Settings;
import com.sf.vas.atjpa.entities.TopUpProfile;
import com.sf.vas.atjpa.entities.TopupHistory;
import com.sf.vas.atjpa.entities.VtuTransactionLog;
import com.sf.vas.atjpa.enums.Status;
import com.sf.vas.mtnvtu.enums.SmsProps;
import com.sf.vas.mtnvtu.enums.VtuMtnSetting;
import com.sf.vas.mtnvtu.enums.VtuVendStatusCode;
import com.sf.vas.mtnvtu.service.VtuMtnAsyncService;
import com.sf.vas.mtnvtu.service.VtuMtnService;
import com.sf.vas.mtnvtu.service.VtuMtnSoapService;
import com.sf.vas.mtnvtu.soapartifacts.HostIFServicePortType;
import com.sf.vas.mtnvtu.soapartifacts.Vend;
import com.sf.vas.mtnvtu.soapartifacts.VendResponse;

/**
 * @author dawuzi
 * The maxSession property was set to one deliberately due to the sequential constraint of the VTU system for setting a sequence number
 */

@MessageDriven(mappedName="java:/jms/queue/VtuQueue", activationConfig = {
		@ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"), 
		@ActivationConfigProperty(propertyName="destination", propertyValue="java:/jms/queue/VtuQueue"), 
		@ActivationConfigProperty(propertyName="maxSession", propertyValue="1"), 
		@ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")})
public class VtuMtnVendListener implements MessageListener {

	@Inject
	VtuMtnSoapService soapService;
	
	@Inject
	VtuMtnQueryService vtuQueryService;
	
	@Inject
	VtuMtnService vtuMtnService;
	
	@Inject
	VtuMtnAsyncService asyncService;
	
	private Logger log = LoggerFactory.getLogger(getClass());
	
	private long currentSequence = 0L;
	
	private Settings vtuCurrentSeqNoSettings;
	
	@PostConstruct
	private void init(){
		try {
			currentSequence = Long.parseLong(vtuQueryService.getSettingValue(VtuMtnSetting.VTU_CURRENT_SEQUENCE_NUMBER));
			vtuCurrentSeqNoSettings = vtuQueryService.getSettingsByName(VtuMtnSetting.VTU_CURRENT_SEQUENCE_NUMBER.name());
		} catch (NumberFormatException e) {
			log.error("Error parsing CURRENT_SEQUENCE_NUMBER setting value. Using default value : "+currentSequence);
		}
	}

	private void updateSequenceNumberSetting(){
		vtuCurrentSeqNoSettings.setValue(String.valueOf(currentSequence)); 
		vtuQueryService.update(vtuCurrentSeqNoSettings);
	}
	
	@Override
	public void onMessage(Message message) {
		ObjectMessage objectMessage = (ObjectMessage) message;
		try {
			
			if(!message.getJMSRedelivered()){
				
				VtuTransactionLog vtuTransactionLog = (VtuTransactionLog) objectMessage.getObject();
				
				try {
					handleVtuRequestMessage(vtuTransactionLog);
				} catch (Exception e) {
					log.error("Error handling vtu request", e);
					vtuTransactionLog.setVtuStatus(Status.FAILED);
					
					TopupHistory topupHistory = vtuTransactionLog.getTopupHistory(); 
					
					topupHistory.setStatus(Status.FAILED);
					topupHistory.setFailureReason("VTU VEND ERROR");
					topupHistory.setDisplayFailureReason("Oops! An error occured while trying to credit your account. Kindly contact support");
					
					vtuQueryService.update(vtuTransactionLog);
					vtuQueryService.update(topupHistory);
				}
			}
		} catch (Exception e) {
			log.error("Error handling vtu request", e);
		}
	}

	/**
	 * @param vtuRequestQueueMessage
	 */
	private void handleVtuRequestMessage(VtuTransactionLog transactionLog) {
		
		if(Status.SUCCESSFUL.equals(transactionLog.getVtuStatus())){
			log.info("skipping successful vtu transaction log with pk : "+transactionLog.getPk());
			return;
		}
		
//		added this for an error scenario where an msisdn with a + sign was previously sent
		String destinationMsisdn = transactionLog.getDestinationMsisdn();
		
		if(destinationMsisdn.startsWith("+")){
			destinationMsisdn = destinationMsisdn.substring(1);
			transactionLog.setDestinationMsisdn(destinationMsisdn); // we need to update the vtu log and remove the plus sign if it present
		}
		
		Vend vend = new Vend();
		
		vend.setAmount(String.valueOf(transactionLog.getAmount().intValue()));
		vend.setDestMsisdn(transactionLog.getDestinationMsisdn());
		vend.setOrigMsisdn(transactionLog.getOriginatorMsisdn());
		vend.setSequence(String.valueOf(currentSequence)); 
		vend.setTariffTypeId(transactionLog.getTariffTypeId());
		
		VendResponse vendResponse = sendVendRequest(vend);
		
		VtuVendStatusCode vendStatusCode = null;
		
		if(vendResponse.getStatusId() != null){
			vendStatusCode = VtuVendStatusCode.from(vendResponse.getStatusId());
		}
		
//		ideally this should only happen once
		while(vendStatusCode != null && VtuVendStatusCode.SEQUENCE_NUMBER_CHECK_FAILED.equals(vendStatusCode)){
			
			log.info("sequence number check failed vendResponse : "+vendResponse);
			
//			this check was done because it was noticed that for sequence number check failed responses, MTN Vend system may not send the 
//			last valid sequence number as indicated in their API document. In that case, we just increment the currentSequence number and retry
			if(vendResponse.getLasseq() != null){
//				Added another try catch block just in case they send a non numeric value too
				try {
					currentSequence = Long.parseLong(vendResponse.getLasseq().trim());
				} catch (NumberFormatException e) {
					log.error("invalid lasseq number sent from MTN Vend Service. lasseq : "+vendResponse.getLasseq());
				}
			} else {
				log.error("invalid lasseq number sent from MTN Vend Service. lasseq is null");
			}
			
			currentSequence++;
			vend.setSequence(String.valueOf(currentSequence));
			vendResponse = sendVendRequest(vend);
			vendStatusCode = null;
			if(vendResponse.getStatusId() != null){
				vendStatusCode = VtuVendStatusCode.from(vendResponse.getStatusId());
			}
		}
		
		TopupHistory topupHistory = transactionLog.getTopupHistory(); 
		
		transactionLog.setSequence(currentSequence);
		
		setVendResponse(vendResponse, transactionLog);
		
		log.info("vend : "+vend);
		log.info("vendResponse : "+vendResponse);
		
//		we update here first once we have gotten a valid response from MTN VTU service. So we can have records even if an exception will be thrown later
		vtuQueryService.update(transactionLog);
		
		CurrentCycleInfo currentCycleInfo = null;
		
		if(vendStatusCode != null && VtuVendStatusCode.SUCCESSFUL.equals(vendStatusCode)){
			
			asyncService.sendSms(SmsProps.CREDIT_SUCCESSFUL, transactionLog.getDestinationMsisdn(), "amount", vend.getAmount());
			
//			this should only be updated for successful transaction
			currentSequence++;
			
			transactionLog.setVtuStatus(Status.SUCCESSFUL);
			topupHistory.setStatus(Status.SUCCESSFUL);
			topupHistory.setFailureReason(null);
			topupHistory.setDisplayFailureReason(null);
			
			TopUpProfile topUpProfile = transactionLog.getTopUpProfile();
			
			if(topUpProfile != null){
			
				currentCycleInfo = vtuMtnService.getCycleInfoCreateIfNotExist(topUpProfile);
				
				currentCycleInfo.setDateModified(new Timestamp(System.currentTimeMillis())); 
				
				
				BigDecimal amount = topupHistory.getAmount();
				
				BigDecimal currentCummulativeAmount = currentCycleInfo.getCurrentCummulativeAmount(); 
				
				BigDecimal newCummulativeAmount = currentCummulativeAmount.add(amount);
				
				BigDecimal topupLimit = topUpProfile.getTopupLimit();
				
				BigDecimal newMaxAmountLeft = topupLimit.subtract(newCummulativeAmount);
				
				currentCycleInfo.setCurrentCummulativeAmount(newCummulativeAmount);
				currentCycleInfo.setMaxAmountLeft(newMaxAmountLeft);
				currentCycleInfo.setLastKnownTopupAmount(amount);
			} else {
//				this would be the case in an instant top up scenario and it is not subject to any top up limit restriction
			}
			
		} else {
			transactionLog.setVtuStatus(Status.FAILED);
			topupHistory.setStatus(Status.FAILED);
			if(vendStatusCode != null){
				topupHistory.setFailureReason("VTU VEND "+vendStatusCode.name());
			} else {
				topupHistory.setFailureReason("VTU VEND ERROR");
			}
			topupHistory.setDisplayFailureReason("Oops! server error, we are unable to credit you at the moment. Kindly contact support");
		}
		
		vtuQueryService.update(transactionLog);
		vtuQueryService.update(topupHistory);
		
		if(currentCycleInfo != null){
			vtuQueryService.update(currentCycleInfo);
		}
		
		updateSequenceNumberSetting();
		
		if(transactionLog.getCallBackUrl() != null && !transactionLog.getCallBackUrl().trim().isEmpty()){
			doCallBack(transactionLog);
		}
	}

	/**
	 * do asynchronously in order not to obstruct further execution
	 * 
	 * @param transactionLog
	 * @param callbackUrl
	 */
	@Asynchronous
	public Future<String> doCallBack(VtuTransactionLog transactionLog) {
		//TODO invoke the client's url with status 
		return new AsyncResult<String>("unimplemented");
	}

	/**
	 * @param vendResponse
	 * @param transactionLog
	 */
	private void setVendResponse(VendResponse vendResponse, VtuTransactionLog transactionLog) {
		transactionLog.setStatusId(vendResponse.getStatusId());
		transactionLog.setTxRefId(vendResponse.getTxRefId());
		transactionLog.setSeqStatus(vendResponse.getSeqstatus());
		transactionLog.setSeqTxRefId(vendResponse.getSeqtxRefdId());
		transactionLog.setLastSeq(vendResponse.getLasseq());
		transactionLog.setOrigBalance(vendResponse.getOrigBalance());
		transactionLog.setDestBalance(vendResponse.getDestBalance());
		transactionLog.setResponseCode(vendResponse.getResponseCode());
		transactionLog.setResponseMessage(vendResponse.getResponseMessage());
	}

	/**
	 * @param vend
	 * @return
	 */
	private VendResponse sendVendRequest(Vend vend) {
		HostIFServicePortType hostIFServicePortType = soapService.getHostIFServicePortType();
		return hostIFServicePortType.vend(vend);
	}
}
