/**
 * 
 */
package com.sf.vas.vend.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sf.vas.atjpa.entities.CurrentCycleInfo;
import com.sf.vas.atjpa.entities.NetworkCarrier;
import com.sf.vas.atjpa.entities.Subscriber;
import com.sf.vas.atjpa.entities.TopUpProfile;
import com.sf.vas.atjpa.entities.TopupHistory;
import com.sf.vas.atjpa.entities.VtuTransactionLog;
import com.sf.vas.atjpa.entities.VtuTransactionLog_;
import com.sf.vas.atjpa.enums.NetworkCarrierType;
import com.sf.vas.atjpa.enums.Status;
import com.sf.vas.utils.exception.VasException;
import com.sf.vas.utils.restartifacts.vtu.AirtimeTransferResponse;
import com.sf.vas.vend.dto.AirtimeTransferRequestDTO;
import com.sf.vas.vend.enums.ResponseCode;
import com.sf.vas.vend.wrappers.GloNgVendWrapperService;
import com.sf.vas.vend.wrappers.IAirtimeTransferHandler;
import com.sf.vas.vend.wrappers.MtnNgVtuWrapperService;

/**
 * @author dawuzi
 *
 */

@Stateless
public class VtuVasService {

	@Inject
	VasVendQueryService vtuQueryService;

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

	public void retriggerSingleFailedTransaction(VtuTransactionLog vtuTransactionLog) throws VasException {

		if(!isValidRetriggerVtuTransactionLog(vtuTransactionLog)){
			//			only failed vtu transactions should be re triggered
			log.info("skipping invalid transaction log");
			return;
		}

		doRetriggerSingleFailedTransaction(vtuTransactionLog); 
	}

	//	Did this to avoid a transaction log being queued multiple times
	@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
	private void doRetriggerSingleFailedTransaction(VtuTransactionLog vtuTransactionLog) throws VasException {

		vtuTransactionLog.setVtuStatus(Status.UNKNOWN); 

		AirtimeTransferRequestDTO airtimeTransferRequestDTO = getAirtimeTransferRequestDTO(vtuTransactionLog);

		handleTransferAirtime(airtimeTransferRequestDTO);
	}

	@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
	public void retriggerFailedTransactionsBatch(List<VtuTransactionLog> vtuTransactionLogs) throws VasException{
		if(vtuTransactionLogs == null || vtuTransactionLogs.isEmpty()){
			return;
		}
		List<VtuTransactionLog> failedTransactions = vtuTransactionLogs.stream()
				.filter(aLog -> isValidRetriggerVtuTransactionLog(aLog)).collect(Collectors.toList());

		if(failedTransactions.isEmpty()){
			return;
		}

		for (VtuTransactionLog vtuTransactionLog : failedTransactions) {

			vtuTransactionLog.setVtuStatus(Status.UNKNOWN); 

			AirtimeTransferRequestDTO airtimeTransferRequestDTO = getAirtimeTransferRequestDTO(vtuTransactionLog);

			handleTransferAirtime(airtimeTransferRequestDTO);

		}
	}

	private boolean isValidRetriggerVtuTransactionLog(VtuTransactionLog vtuTransactionLog){
		return vtuTransactionLog != null && Status.FAILED.equals(vtuTransactionLog.getVtuStatus());
	}

	private AirtimeTransferRequestDTO getAirtimeTransferRequestDTO(VtuTransactionLog vtuTransactionLog) {

		VtuTransactionLog eagerLog = vtuQueryService.getByPkWithEagerLoading(VtuTransactionLog.class, vtuTransactionLog.getPk()
				, VtuTransactionLog_.sender, VtuTransactionLog_.topUpProfile, VtuTransactionLog_.networkCarrier);

		AirtimeTransferRequestDTO airtimeTransferRequestDTO = new AirtimeTransferRequestDTO();

		airtimeTransferRequestDTO.setAmount(vtuTransactionLog.getAmount());
		airtimeTransferRequestDTO.setCallbackUrl(vtuTransactionLog.getCallBackUrl());
		airtimeTransferRequestDTO.setMsisdn(vtuTransactionLog.getDestinationMsisdn());
		airtimeTransferRequestDTO.setNetworkCarrier(eagerLog.getNetworkCarrier());
		airtimeTransferRequestDTO.setSubscriber(eagerLog.getSender());
		airtimeTransferRequestDTO.setTopupHistory(vtuTransactionLog.getTopupHistory());
		airtimeTransferRequestDTO.setTopUpProfile(eagerLog.getTopUpProfile());
		airtimeTransferRequestDTO.setVtuTransactionLog(vtuTransactionLog); 

		return airtimeTransferRequestDTO;
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
