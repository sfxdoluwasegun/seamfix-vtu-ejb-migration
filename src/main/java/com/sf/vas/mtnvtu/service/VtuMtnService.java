/**
 * 
 */
package com.sf.vas.mtnvtu.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.jms.JMSException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sf.vas.atjpa.entities.CurrentCycleInfo;
import com.sf.vas.atjpa.entities.NetworkCarrier;
import com.sf.vas.atjpa.entities.Subscriber;
import com.sf.vas.atjpa.entities.TopUpProfile;
import com.sf.vas.atjpa.entities.TopupHistory;
import com.sf.vas.atjpa.entities.VtuTransactionLog;
import com.sf.vas.atjpa.enums.NetworkCarrierType;
import com.sf.vas.atjpa.enums.Status;
import com.sf.vas.mtnvtu.enums.ResponseCode;
import com.sf.vas.mtnvtu.enums.VtuMtnSetting;
import com.sf.vas.mtnvtu.enums.VtuVendStatusCode;
import com.sf.vas.mtnvtu.tools.VtuMtnJmsManager;
import com.sf.vas.mtnvtu.tools.VtuMtnQueryService;
import com.sf.vas.utils.exception.VasException;
import com.sf.vas.utils.restartifacts.vtu.AirtimeTransferResponse;
import com.sf.vas.utils.restartifacts.vtu.AirtimeTransferStatusResponse;
import com.sf.vas.vend.dto.AirtimeTransferRequestDTO;
import com.sf.vas.vend.wrappers.GloNgVendWrapperService;
import com.sf.vas.vend.wrappers.MtnNgVtuWrapperService;
import com.sf.vas.vtu.IAirtimeTransferHandler;

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
	
	@Inject
	MtnNgVtuWrapperService mtnNgVtuWrapperService;
	
	@Inject
	GloNgVendWrapperService gloNgVendWrapperService;
	
	private Logger log = LoggerFactory.getLogger(getClass());
	
	public AirtimeTransferResponse handleTransferAirtime(AirtimeTransferRequestDTO request){
		
		AirtimeTransferResponse response = new AirtimeTransferResponse();
		
		Subscriber subscriber = request.getSubscriber();
		
		if(subscriber == null){
			response.assignResponseCode(ResponseCode.UNKNOWN_USER);
			return response;
		}
		
		NetworkCarrier carrier = request.getNetworkCarrier();
		
		if(carrier == null){
			response.assignResponseCode(ResponseCode.UNKNOWN_NETWORK_CARRIER);
			return response;
		}
		
		TopupHistory topupHistory = request.getTopupHistory();
		
		if(topupHistory == null){
			response.assignResponseCode(ResponseCode.UNKNOWN_TOP_UP_HISTORY);
			return response;
		}
		
		NetworkCarrierType type = carrier.getType() == null ? NetworkCarrierType.MTN_NG : carrier.getType();
		
		IAirtimeTransferHandler handler = getAirtimeTransferHandler(type);
		
		return handler.handleTransferAirtime(request);

	}

	private IAirtimeTransferHandler getAirtimeTransferHandler(NetworkCarrierType type) {

		log.info("type : "+type);
		
		type = type == null ? NetworkCarrierType.MTN_NG : type;
		
		switch (type) {
		
		case MTN_NG:
			return mtnNgVtuWrapperService;
		case GLO_NG:
			return gloNgVendWrapperService;

		default:
			return mtnNgVtuWrapperService;
		}
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
	private void doRetriggerSingleFailedTransaction(VtuTransactionLog vtuTransactionLog) throws VasException {
		
		String originMsisdn = vtuQueryService.getSettingValue(VtuMtnSetting.VTU_ORIGINATOR_MSISDN);
		
		vtuTransactionLog.setVtuStatus(Status.UNKNOWN); 
		vtuTransactionLog.setOriginatorMsisdn(originMsisdn); // we need to always use the updated originator msisdn
		
		vtuQueryService.update(vtuTransactionLog);
		
		try {
			jmsManager.sendVtuRequest(vtuTransactionLog);
		} catch (JMSException e) {
			throw new VasException(e);
		}
	}
	
	@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
	public void retriggerFailedTransactionsBatch(List<VtuTransactionLog> vtuTransactionLogs) throws VasException{
		if(vtuTransactionLogs == null || vtuTransactionLogs.isEmpty()){
			return;
		}
		List<VtuTransactionLog> failedTransactions = vtuTransactionLogs.stream()
				.filter(log -> (log != null && Status.FAILED.equals(log.getVtuStatus()))).collect(Collectors.toList());
		
		if(failedTransactions.isEmpty()){
			return;
		}
		
		String originMsisdn = vtuQueryService.getSettingValue(VtuMtnSetting.VTU_ORIGINATOR_MSISDN);

		for (VtuTransactionLog vtuTransactionLog : failedTransactions) {
			
			vtuTransactionLog.setVtuStatus(Status.UNKNOWN); 
			vtuTransactionLog.setOriginatorMsisdn(originMsisdn); // we need to always use the updated originator msisdn
			
			vtuQueryService.update(vtuTransactionLog);
			
			try {
				jmsManager.sendVtuRequest(vtuTransactionLog);
			} catch (JMSException e) {
				throw new VasException(e);
			}
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
	
	//moved this method to this class in order to access getTariffTypeId method which is private to this class - please confirm
	public VtuTransactionLog createVtuLog(TopupHistory topupHistory,TopUpProfile topUpProfile,Subscriber subscriber,NetworkCarrier networkCarrier) {
		
		VtuTransactionLog transactionLog = new VtuTransactionLog();
		
		String originMsisdn = vtuQueryService.getSettingValue(VtuMtnSetting.VTU_ORIGINATOR_MSISDN);
		String serviceProviderId = vtuQueryService.getSettingValue(VtuMtnSetting.VTU_SERVICE_PROVIDER_ID);
		
		transactionLog.setAmount(topupHistory.getAmount());
		transactionLog.setCallBackUrl(null); //please confirm this
		transactionLog.setOriginatorMsisdn(originMsisdn); //please confirm this
		transactionLog.setDestinationMsisdn(topupHistory.getMsisdn());
		transactionLog.setSender(subscriber);
		transactionLog.setTariffTypeId(getTariffTypeId(topupHistory.getAmount()));//get tariff id please confirm this
		transactionLog.setTopupHistory(topupHistory); 
		transactionLog.setTopUpProfile(topUpProfile); 
		transactionLog.setServiceProviderId(serviceProviderId);
		transactionLog.setNetworkCarrier(networkCarrier);
		transactionLog.setVtuStatus(Status.PENDING);
		
		vtuQueryService.createImmediately(transactionLog);

		return transactionLog;
	}
}
