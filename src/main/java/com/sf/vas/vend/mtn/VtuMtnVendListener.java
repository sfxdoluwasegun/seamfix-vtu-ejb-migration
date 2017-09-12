/**
 * 
 */
package com.sf.vas.vend.mtn;

import java.net.ConnectException;
import javax.annotation.PostConstruct;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJBTransactionRolledbackException;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sf.vas.airtimevend.mtn.dto.VendDto;
import com.sf.vas.airtimevend.mtn.dto.VendResponseDto;
import com.sf.vas.airtimevend.mtn.enums.MtnVtuVendStatusCode;
import com.sf.vas.airtimevend.mtn.soapartifacts.VendResponse;
import com.sf.vas.atjpa.entities.Settings;
import com.sf.vas.atjpa.entities.TopupHistory;
import com.sf.vas.atjpa.entities.VtuTransactionLog;
import com.sf.vas.atjpa.enums.Status;
import com.sf.vas.vend.enums.VasVendSetting;
import com.sf.vas.vend.service.VasVendQueryService;
import com.sf.vas.vend.service.VendService;
import com.sf.vas.vend.service.VtuMtnAsyncService;
import com.sf.vas.vend.service.VtuVasService;
import com.sf.vas.vend.util.VendUtil;
import com.sf.vas.vend.wrappers.MtnNgVtuWrapperService;

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
	VasVendQueryService vtuQueryService;
	
	@Inject
	VtuVasService vtuMtnService;
	
	@Inject
	VtuMtnAsyncService asyncService;
	
	@Inject
	MtnNgVtuWrapperService mtnNgVtuWrapperService;
	
	@Inject
	VendService vendService;
	
	private Logger log = LoggerFactory.getLogger(getClass());
	
	private long currentSequence = 0L;
	
	private Settings vtuCurrentSeqNoSettings;
	
	private VendUtil vendUtil = new VendUtil();
	
	@PostConstruct
	private void init(){
		
		try {
			currentSequence = Long.parseLong(vtuQueryService.getSettingValue(VasVendSetting.VTU_CURRENT_SEQUENCE_NUMBER));
			vtuCurrentSeqNoSettings = vtuQueryService.getSettingsByName(VasVendSetting.VTU_CURRENT_SEQUENCE_NUMBER.name());
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
					
					TopupHistory topupHistory = vtuTransactionLog.getTopupHistory(); 
					
					topupHistory.setFailureReason("VTU VEND ERROR");
					topupHistory.setDisplayFailureReason("Oops! An error occured while trying to credit your account. Kindly contact support");
					
					if(vendUtil.isThrowableClassInStrackTrace(e, EJBTransactionRolledbackException.class)){
						vendService.handleFailedVendingWithNewTransaction(vtuTransactionLog);
					} else {
						vendService.handleFailedVending(vtuTransactionLog);
					}
				}
			}
		} catch (Exception e) {
			log.error("Outter Error handling vtu request", e);
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
		}
		
		VendDto vendDto = new VendDto();
		
		vendDto.setAmount(transactionLog.getAmount().intValue());
		vendDto.setDestMsisdn(destinationMsisdn);
		vendDto.setOrigMsisdn(transactionLog.getOriginatorMsisdn());
		vendDto.setSequence(currentSequence);
		vendDto.setTariffTypeId(transactionLog.getTariffTypeId());
		
		log.info("vendDto : "+vendDto);
		
		VendResponseDto vendResponseDto = null;
		MtnVtuVendStatusCode vendStatusCode = null;
		
		Exception vendException = null;
		
		try {
			vendResponseDto = mtnNgVtuWrapperService.sendVendRequest(vendDto);
		} catch (Exception e) {
			log.error("Error reaching vtu", e);
			vendException = e;
		}
		
		TopupHistory topupHistory = transactionLog.getTopupHistory(); 

		if(vendResponseDto == null){
			
			topupHistory.setFailureReason("VTU VEND ERROR");
			topupHistory.setDisplayFailureReason(getDisplayFailureReason(vendStatusCode));
			
			boolean isRollbackCausedException = vendUtil.isThrowableClassInStrackTrace(vendException, EJBTransactionRolledbackException.class);
			boolean connectionException = vendUtil.isThrowableClassInStrackTrace(vendException, ConnectException.class);
			
			if(isRollbackCausedException){
				vendService.handleFailedVendingWithNewTransaction(transactionLog, connectionException);
			} else {
				vendService.handleFailedVending(transactionLog, connectionException);
			}
		} else {
			
			currentSequence = vendResponseDto.getUsedSequence();
			
			transactionLog.setSequence(currentSequence);
			
			setVendResponse(vendResponseDto, transactionLog);
			
			log.info("vendResponse : "+vendResponseDto);
			
//			we update here first once we have gotten a valid response from MTN VTU service. So we can have records even if an exception will be thrown later
			vtuQueryService.update(transactionLog);
			
			vendStatusCode = vendResponseDto.getStatusCode();

			if(vendStatusCode != null && MtnVtuVendStatusCode.SUCCESSFUL.equals(vendStatusCode)){
				
//				this should only be updated for successful transaction
				currentSequence++;
				
				vendService.handleSuccessfulVending(transactionLog);
				
				updateSequenceNumberSetting();
			} else {
				
				if(vendStatusCode != null){
					topupHistory.setFailureReason("VTU VEND "+vendStatusCode.name());
				} else {
					topupHistory.setFailureReason("VTU VEND ERROR");
				}
				topupHistory.setDisplayFailureReason(getDisplayFailureReason(vendStatusCode));
				
				vendService.handleFailedVending(transactionLog);
			}
		}
	}

	private String getDisplayFailureReason(MtnVtuVendStatusCode vendStatusCode) {
		
		String defaultReason = "Oops! server error, we are unable to credit you at the moment. Kindly contact support";
		
		if(vendStatusCode == null){
			return defaultReason;
		}
		
		switch (vendStatusCode) {
		case MSISDN_BARRED:
			return "Oops ! Could not transfer airtime. Reason : Phone number barred";
		case INVALID_MSISDN:
			return "Oops ! Could not transfer airtime. Reason : Invalid MTN phone number";
		case TEMPORARY_INVALID_MSISDN:
			return "Oops ! Could not transfer airtime. Reason : Temporary Invalid MTN phone number";

		default:
			return defaultReason;
		}
	}

	/**
	 * @param vendResponse
	 * @param transactionLog
	 */
	private void setVendResponse(VendResponseDto vendResponseDto, VtuTransactionLog transactionLog) {
		
		if(vendResponseDto.getVendResponse() == null){
			log.warn("vendResponseDto is null for vtu log : "+transactionLog.getPk());
			return;
		}
		
		VendResponse vendResponse = vendResponseDto.getVendResponse();
		
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

}
