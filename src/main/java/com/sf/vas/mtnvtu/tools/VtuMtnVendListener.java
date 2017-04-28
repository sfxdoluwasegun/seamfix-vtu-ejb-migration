/**
 * 
 */
package com.sf.vas.mtnvtu.tools;

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

import com.sf.vas.atjpa.entities.Settings;
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
					
					vtuQueryService.update(vtuTransactionLog);
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
		
		log.info("vendStatusCode : "+vendStatusCode);
		
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
			log.info("vendStatusCode : "+vendStatusCode);
		}
		
		log.info("vendResponse : "+vendResponse);
		
		TopupHistory topupHistory = transactionLog.getTopupHistory(); 
		
		transactionLog.setSequence(currentSequence);
		
		setVendResponse(vendResponse, transactionLog);
		
		if(vendStatusCode != null && VtuVendStatusCode.SUCCESSFUL.equals(vendStatusCode)){
			transactionLog.setVtuStatus(Status.SUCCESSFUL);
			topupHistory.setStatus(Status.SUCCESSFUL);
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
		
//		log.warn("XXXXXXXXXXXXXXXXXXXX TEST VEND RESPONSE IN USE XXXXXXXXXXXXXXXXXXXX");
//		
//		VendResponse response = new VendResponse();
//		
//		response.setDestBalance("100");
//		response.setDestMsisdn("080236438383");
//		
//		int testCase = Integer.parseInt(vend.getSequence()) % 4 ;
//		
//		switch (testCase) {
//		
//		case 0:
//			response.setStatusId(VtuVendStatusCode.SUCCESSFUL.getResponseCode());
//			break;
//		case 1:
//			String lasseq = String.valueOf(currentSequence + (int)(Math.random() * 10 + 1));
//			response.setLasseq(lasseq);
//			response.setStatusId(VtuVendStatusCode.SEQUENCE_NUMBER_CHECK_FAILED.getResponseCode());
//			break;
//		case 2:
//			response.setStatusId(VtuVendStatusCode.INVALID_DESTINATION_MSISDN.getResponseCode());
//			break;
//		case 3:
//			response.setStatusId(VtuVendStatusCode.MSISDN_BARRED.getResponseCode());
//			break;
//		default:
//			break;
//		}
//		
//		return response;
		
		HostIFServicePortType hostIFServicePortType = soapService.getHostIFServicePortType();
		return hostIFServicePortType.vend(vend);
	}
}
