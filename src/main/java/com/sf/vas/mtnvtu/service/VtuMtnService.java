/**
 * 
 */
package com.sf.vas.mtnvtu.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.jms.JMSException;

import org.jboss.logging.Logger;

import com.sf.vas.atjpa.entities.CurrentCycleInfo;
import com.sf.vas.atjpa.entities.NetworkCarrier;
import com.sf.vas.atjpa.entities.Subscriber;
import com.sf.vas.atjpa.entities.TopUpProfile;
import com.sf.vas.atjpa.entities.TopupHistory;
import com.sf.vas.atjpa.entities.VtuTransactionLog;
import com.sf.vas.atjpa.enums.Status;
import com.sf.vas.mtnvtu.enums.ResponseCode;
import com.sf.vas.mtnvtu.enums.VtuMtnSetting;
import com.sf.vas.mtnvtu.enums.VtuVendStatusCode;
import com.sf.vas.mtnvtu.tools.VtuMtnJmsManager;
import com.sf.vas.mtnvtu.tools.VtuMtnQueryService;
import com.sf.vas.utils.exception.VasException;
import com.sf.vas.utils.exception.VasRuntimeException;
import com.sf.vas.utils.restartifacts.vtu.AirtimeTransferRequest;
import com.sf.vas.utils.restartifacts.vtu.AirtimeTransferResponse;
import com.sf.vas.utils.restartifacts.vtu.AirtimeTransferStatusResponse;

/**
 * @author dawuzi
 *
 */

@Stateless
public class VtuMtnService {

	@Inject
	VtuMtnQueryService vtuQueryService;
	
	@Inject
	VtuMtnJmsManager jmsManager;
	
	Logger log = Logger.getLogger(getClass());
	
	public AirtimeTransferResponse handleTransferAirtime(AirtimeTransferRequest request){
		
		AirtimeTransferResponse response = new AirtimeTransferResponse();
		
		Subscriber subscriber = vtuQueryService.getByPk(Subscriber.class, request.getUserId());
		
		if(subscriber == null){
			response.assignResponseCode(ResponseCode.UNKNOWN_USER);
			return response;
		}
		
		TopUpProfile topUpProfile = null;
		
		if(request.getTopUpProfileId() != null){
			topUpProfile = vtuQueryService.getByPk(TopUpProfile.class, request.getTopUpProfileId());
			if(topUpProfile == null){
				response.assignResponseCode(ResponseCode.UNKNOWN_TOP_UP_PROFILE);
				return response;
			}
		}
		
		NetworkCarrier carrier = vtuQueryService.getNetworkCarrierByName(request.getNetworkCarrier());
		
		if(carrier == null){
			response.assignResponseCode(ResponseCode.UNKNOWN_NETWORK_CARRIER);
			return response;
		}
		
		TopupHistory topupHistory = vtuQueryService.getByPk(TopupHistory.class, request.getTopUpHistoryId());
		
		if(topupHistory == null){
			response.assignResponseCode(ResponseCode.UNKNOWN_TOP_UP_HISTORY);
			return response;
		}
		
		String originMsisdn = vtuQueryService.getSettingValue(VtuMtnSetting.VTU_ORIGINATOR_MSISDN);
		String serviceProviderId = vtuQueryService.getSettingValue(VtuMtnSetting.VTU_SERVICE_PROVIDER_ID);
		
		VtuTransactionLog transactionLog = new VtuTransactionLog();
		
		transactionLog.setAmount(request.getAmount());
		transactionLog.setCallBackUrl(request.getCallbackUrl());
		transactionLog.setOriginatorMsisdn(originMsisdn);
		transactionLog.setDestinationMsisdn(request.getMsisdn());
		transactionLog.setSender(subscriber);
		transactionLog.setTariffTypeId(getTariffTypeId(request.getAmount()));
		transactionLog.setTopupHistory(topupHistory); 
		transactionLog.setTopUpProfile(topUpProfile); 
		transactionLog.setServiceProviderId(serviceProviderId);
		transactionLog.setNetworkCarrier(carrier);
		transactionLog.setVtuStatus(Status.PENDING);
		
		vtuQueryService.createImmediately(transactionLog);
		
		try {
			jmsManager.sendVtuRequest(transactionLog);
		} catch (JMSException e) {
			throw new VasRuntimeException(e);
		}
		
		response.assignResponseCode(ResponseCode.SUCCESS);
		response.setTransactionId(transactionLog.getPk());
		
		return response;
	}

	/**
	 * this houses the logic for tariff type ids for amount based on the clarification from the VTU service providers
	 * @param amount
	 * @return
	 */
	private String getTariffTypeId(BigDecimal amount) {
		return "4";
	}


	/**
	 * @param transactionId
	 * @return
	 */
	public AirtimeTransferStatusResponse handleAirtimeTransferRequestStatus(String transactionId) {

		AirtimeTransferStatusResponse response = new AirtimeTransferStatusResponse();
		
		long tId;
		
		try {
			tId = Long.parseLong(transactionId);
		} catch (NumberFormatException e) {
			response.assignResponseCode(ResponseCode.INVALID_REQUEST);
			return response;
		}
		
		VtuTransactionLog transactionLog = vtuQueryService.getByPk(VtuTransactionLog.class, tId);
		
		if(transactionLog == null){
			response.assignResponseCode(ResponseCode.INVALID_REQUEST);
			return response;
		}
		
//		this success status code simply implies that the client REST call was successful. 
//		The status field of the response POJO should be examined to obtain the actual status of the transaction with the target transaction ID
		response.assignResponseCode(ResponseCode.SUCCESS);
		
		response.setStatus(transactionLog.getVtuStatus());
		
//		these status are pretty clear what the status is 
		if(Status.PENDING.equals(transactionLog.getVtuStatus())
				|| Status.SUCCESSFUL.equals(transactionLog.getVtuStatus())){
			return response;
		}
		
//		for FAILED and UNKNOWN statuses a reason can give more insights depending on the status from MTN
		VtuVendStatusCode vendStatusCode = null;

		if(transactionLog.getStatusId() != null){
			vendStatusCode = VtuVendStatusCode.from(transactionLog.getStatusId());
		}
		
//		this should not happen
		if(vendStatusCode == null){
			response.setReason("An error occurred processing the request");
			return response;
		}
		
		switch (vendStatusCode) {
		
		case INVALID_DESTINATION_MSISDN:
		case INVALID_MSISDN:
			response.setReason("Invalid phone number specified");
			break;
		case MSISDN_BARRED:
			response.setReason("Phone number barred");
			break;
		case TEMPORARY_INVALID_MSISDN:
			response.setReason("Phone number is temporarily invalid");
			break;
//			for other ones just a generic error message should suffice
		default:
			response.setReason("An error occurred processing the request");
			break;
		}
		
		return response;
	}
	
	public void retriggerSingleFailedTransaction(VtuTransactionLog vtuTransactionLog) throws VasException {
		
		if(vtuTransactionLog == null || !Status.FAILED.equals(vtuTransactionLog.getVtuStatus())){
//			only failed vtu transactions should be re triggered
			return;
		}
		
		doRetriggerSingleFailedTransaction(vtuTransactionLog); 
	}
	
//	Did this to avoid a transaction log being queued multiple times
	@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
	public void doRetriggerSingleFailedTransaction(VtuTransactionLog vtuTransactionLog) throws VasException {
		
		vtuTransactionLog.setVtuStatus(Status.UNKNOWN); 
		vtuQueryService.update(vtuTransactionLog);
		
		try {
			jmsManager.sendVtuRequest(vtuTransactionLog);
		} catch (JMSException e) {
			throw new VasException(e);
		}
	}
	
	public void retriggerFailedVendTransactions() {
		List<VtuTransactionLog> failedTransactionLogs = vtuQueryService.getFailedTransactionLogs();
		
		if(failedTransactionLogs == null || failedTransactionLogs.isEmpty()){
			return;
		}

		for(VtuTransactionLog vtuTransactionLog : failedTransactionLogs){
			try {
				retriggerSingleFailedTransaction(vtuTransactionLog);
			} catch (VasException e) {
				log.error("Error retrigger log with pk : "+vtuTransactionLog.getPk(), e);
			} 
		}
	}
	
	/**
	 * @param profile
	 * @return
	 */
	private CurrentCycleInfo getNewCycleInfo(TopUpProfile profile) {

		CurrentCycleInfo cycleInfo = new CurrentCycleInfo();
		
		cycleInfo.setCurrentCummulativeAmount(BigDecimal.ZERO);
		cycleInfo.setDateModified(new Timestamp(System.currentTimeMillis()));
		cycleInfo.setDeleted(profile.isDeleted());
		cycleInfo.setLastKnownCycle(profile.getTopupcycle());
		cycleInfo.setLastKnownTopupAmount(profile.getTopUpAmount());
		cycleInfo.setMaxAmountLeft(profile.getTopupLimit());
		cycleInfo.setMsisdn(profile.getMsisdn());
		cycleInfo.setTopUpProfile(profile); 
		
		vtuQueryService.create(cycleInfo);
		
		return cycleInfo;
	}
	
	public CurrentCycleInfo getCycleInfoCreateIfNotExist(TopUpProfile profile) {
		
		CurrentCycleInfo cycleInfo = vtuQueryService.getCurrentCycleInfo(profile.getPk(), profile.getMsisdn());
		
		if(cycleInfo != null){
			return cycleInfo;
		}
		
		return getNewCycleInfo(profile);
	}
	
	
}
