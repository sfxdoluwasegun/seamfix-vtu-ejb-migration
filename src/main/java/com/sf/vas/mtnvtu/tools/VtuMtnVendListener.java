/**
 * 
 */
package com.sf.vas.mtnvtu.tools;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;

import org.jboss.logging.Logger;

import com.sf.vas.atjpa.entities.CurrentCycleInfo;
import com.sf.vas.atjpa.entities.Settings;
import com.sf.vas.atjpa.entities.TopUpProfile;
import com.sf.vas.atjpa.entities.TopupHistory;
import com.sf.vas.atjpa.entities.VtuTransactionLog;
import com.sf.vas.atjpa.enums.Status;
import com.sf.vas.mtnvtu.enums.VtuMtnSetting;
import com.sf.vas.mtnvtu.enums.VtuVendStatusCode;
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
	
	private Logger log = Logger.getLogger(getClass());
	
	private long currentSequence = 0L;
	
	@PostConstruct
	private void init(){
		try {
			currentSequence = Long.parseLong(vtuQueryService.getSettingValue(VtuMtnSetting.VTU_CURRENT_SEQUENCE_NUMBER));
		} catch (NumberFormatException e) {
			log.error("Error parsing CURRENT_SEQUENCE_NUMBER setting value. Using default value : "+currentSequence);
		}
	}
	
	@PreDestroy
	private void wrapUp(){
		log.info("VtuVendListener wrapUp called !!");
		Settings settings = vtuQueryService.getSettingsByName(VtuMtnSetting.VTU_CURRENT_SEQUENCE_NUMBER.name());
		settings.setValue(String.valueOf(currentSequence)); 
		vtuQueryService.update(settings);
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
		
		Vend vend = new Vend();
		
		vend.setAmount(transactionLog.getAmount().toPlainString());
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
			currentSequence = Long.parseLong(vendResponse.getLasseq());
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
		
		CurrentCycleInfo currentCycleInfo = null;
		
		if(vendStatusCode != null && VtuVendStatusCode.SUCCESSFUL.equals(vendStatusCode)){
			transactionLog.setVtuStatus(Status.SUCCESSFUL);
			topupHistory.setStatus(Status.SUCCESSFUL);
			
			TopUpProfile topUpProfile = transactionLog.getTopUpProfile();
			
			currentCycleInfo = vtuQueryService.getCurrentCycleInfo(topUpProfile.getPk(), topUpProfile.getMsisdn());
			
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
			transactionLog.setVtuStatus(Status.FAILED);
			topupHistory.setStatus(Status.FAILED);
			if(vendStatusCode != null){
				topupHistory.setFailureReason("VTU VEND "+vendStatusCode.name());
			} else {
				topupHistory.setFailureReason("VTU VEND ERROR");
			}
		}
		
		vtuQueryService.update(transactionLog);
		vtuQueryService.update(topupHistory);
		
		if(currentCycleInfo != null){
			vtuQueryService.update(currentCycleInfo);
		}
		
		currentSequence++;
		
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
