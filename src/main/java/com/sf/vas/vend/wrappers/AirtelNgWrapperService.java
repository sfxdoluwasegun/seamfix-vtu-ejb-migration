package com.sf.vas.vend.wrappers;

import java.net.ConnectException;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.ejb.EJBTransactionRolledbackException;
import javax.ejb.Stateless;
import javax.inject.Inject;

import com.sf.vas.airtimevend.airtel.artifacts.response.COMMAND;
import com.sf.vas.airtimevend.airtel.dto.AirtelDataRequestParams;
import com.sf.vas.airtimevend.airtel.dto.AirtelInitParams;
import com.sf.vas.airtimevend.airtel.dto.AirtelVendRequestParams;
import com.sf.vas.airtimevend.airtel.dto.CommandResponseDto;
import com.sf.vas.airtimevend.airtel.enums.AirtelResponseCode;
import com.sf.vas.airtimevend.airtel.service.AirtelService;
import com.sf.vas.atjpa.entities.TopupHistory;
import com.sf.vas.atjpa.entities.VtuTransactionLog;
import com.sf.vas.atjpa.enums.Status;
import com.sf.vas.atjpa.enums.TransactionType;
import com.sf.vas.utils.crypto.EncryptionUtil;
import com.sf.vas.utils.exception.VasException;
import com.sf.vas.utils.exception.VasRuntimeException;
import com.sf.vas.utils.restartifacts.vtu.AirtimeTransferResponse;
import com.sf.vas.vend.dto.AirtimeTransferRequestDTO;
import com.sf.vas.vend.enums.ResponseCode;
import com.sf.vas.vend.enums.VasVendSetting;
import com.sf.vas.vend.service.VasVendQueryService;
import com.sf.vas.vend.service.VendService;

/**
 * @author DAWUZI
 *
 */

@Stateless
public class AirtelNgWrapperService extends AbstractAirtimeTransferHandler {

	private AirtelService airtelService;
	
	@Inject
	VendService vendService;
	
	@Inject
	private VasVendQueryService queryService;

	@Inject
	private EncryptionUtil encryptionUtil;
	
	private final String DEFAULT_NOT_APPLICABLE = "NA";
	
	@PostConstruct
	private void init(){
		
		String initPath = queryService.getSettingValue(VasVendSetting.AIRTEL_SERVICE_INIT_PATH);
		String retailerMsisdn = queryService.getSettingValue(VasVendSetting.AIRTEL_RETAILER_MSISDN);
		String encPassword = queryService.getSettingValue(VasVendSetting.AIRTEL_PASSWORD);
		String encPin = queryService.getSettingValue(VasVendSetting.AIRTEL_PIN);
		
		String password;
		String pin;
		
		try {
			password = encryptionUtil.decrypt(encPassword);
			pin = encryptionUtil.decrypt(encPin);
		} catch (VasException e) {
			throw new VasRuntimeException("error decrypting configured credit switch public/private keys", e);
		}
		
		if(password == null || password.trim().isEmpty() || pin == null || pin.trim().isEmpty()){
			log.error("password : "+password+", pin : "+pin);
			throw new VasRuntimeException("airtel password or pin are null or empty after decrypting");
		}
		
		AirtelInitParams initParams = new AirtelInitParams();
		
		initParams.setPassword(password);
		initParams.setPin(pin);
		initParams.setRetailerMsisdn(retailerMsisdn);
		initParams.setServiceInitPath(initPath);
		
		airtelService = new AirtelService(initParams);
	}	
	
	@Override
	public AirtimeTransferResponse handleTransferAirtime(AirtimeTransferRequestDTO request) {
		
		AirtelInitParams initParams = airtelService.getInitParams();

		String ref = getReference(20);
		
		VtuTransactionLog transactionLog;
		
		if(request.getVtuTransactionLog() != null){
			transactionLog = request.getVtuTransactionLog();
		} else {
			transactionLog = new VtuTransactionLog();
		}
		
		transactionLog.setAmount(request.getAmount());
		transactionLog.setCallBackUrl(request.getCallbackUrl());
		transactionLog.setClientReference(ref);
		transactionLog.setOriginatorMsisdn(initParams.getRetailerMsisdn());
		transactionLog.setDataPlan(request.getTopupHistory().getDataPlan()); 
		transactionLog.setDestinationMsisdn(request.getMsisdn());
		transactionLog.setSender(request.getSubscriber());
		transactionLog.setTariffTypeId(DEFAULT_NOT_APPLICABLE);
		transactionLog.setTopupHistory(request.getTopupHistory()); 
		transactionLog.setTopUpProfile(request.getTopUpProfile()); 
		transactionLog.setRoleType(request.getRoleType());
		transactionLog.setServiceProviderId(DEFAULT_NOT_APPLICABLE);
		transactionLog.setNetworkCarrier(request.getNetworkCarrier());
		transactionLog.setVtuStatus(Status.PENDING);
		
		if(transactionLog.getPk() != null){
			queryService.update(transactionLog);
			log.info("after updating the txn log"); 
		} else {
			queryService.createImmediately(transactionLog);
		}
		
		AirtelVendRequestParams airtelVendRequestParams = new AirtelVendRequestParams();
		
		airtelVendRequestParams.setAmount(request.getAmount());
		airtelVendRequestParams.setMsisdn(request.getMsisdn());
		airtelVendRequestParams.setReference(ref);
		
		Exception vendException = null;
		CommandResponseDto commandResponseDto = null;
		
		try {
			commandResponseDto = getCommandResponseDto(request, ref);
		} catch (Exception e) {
			log.error("Error calling airtel service", e);
			vendException = e;
		}
		
		TopupHistory topupHistory = transactionLog.getTopupHistory();
		
		setTopupResponse(transactionLog, commandResponseDto);
		
		log.info("commandResponseDto : "+commandResponseDto);
		
		if(commandResponseDto == null){
			
			topupHistory.setFailureReason("AIRTEL VEND ERROR");
			topupHistory.setDisplayFailureReason(getDisplayFailureReason(null));
			
			boolean isRollbackCausedException = vendUtil.isThrowableClassInStrackTrace(vendException, EJBTransactionRolledbackException.class);
			boolean connectionException = vendUtil.isThrowableClassInStrackTrace(vendException, ConnectException.class);
			
			if(isRollbackCausedException){
				vendService.handleFailedVendingWithNewTransaction(transactionLog, connectionException);
			} else {
				vendService.handleFailedVending(transactionLog, connectionException);
			}
			
		} else {
			
			AirtelResponseCode responseCode = commandResponseDto.getResponseCode();
			
			if(responseCode != null && AirtelResponseCode.SUCCESS.equals(responseCode)){
				vendService.handleSuccessfulVending(transactionLog);
			} else {
				
				if(responseCode != null){
					topupHistory.setFailureReason("AIRTEL VEND "+responseCode.name());
				} else {
					topupHistory.setFailureReason("AIRTEL VEND ERROR");
				}
				topupHistory.setDisplayFailureReason(getDisplayFailureReason(responseCode));
				
				vendService.handleFailedVending(transactionLog);
			}
		}
		
		AirtimeTransferResponse response = new AirtimeTransferResponse();
		
		response.assignResponseCode(ResponseCode.SUCCESS);
		response.setTransactionId(transactionLog.getPk());
		
		return response;
	}

	private CommandResponseDto getCommandResponseDto(AirtimeTransferRequestDTO request, String ref) {

		CommandResponseDto commandResponseDto;
		
		if(TransactionType.DATA.equals(request.getTopupHistory().getTransactionType())){
			
			if(request.getTopupHistory().getDataPlan() == null){
				throw new IllegalArgumentException("DATA transaction type requires a data plan. TopupHistory pk : "
						+request.getTopupHistory().getPk());
			}
			
			AirtelDataRequestParams airtelDataRequestParams = new AirtelDataRequestParams();
			
			airtelDataRequestParams.setMsisdn(request.getMsisdn());
			airtelDataRequestParams.setPlan(request.getTopupHistory().getDataPlan().getPlanId());
			airtelDataRequestParams.setReference(ref);
			
			commandResponseDto = airtelService.sendDataRequest(airtelDataRequestParams);
			
		} else {
		
			AirtelVendRequestParams airtelVendRequestParams = new AirtelVendRequestParams();
			
			airtelVendRequestParams.setAmount(request.getAmount());
			airtelVendRequestParams.setMsisdn(request.getMsisdn());
			airtelVendRequestParams.setReference(ref);
			
			commandResponseDto = airtelService.sendAirtimeRequest(airtelVendRequestParams);
		}
		
		return commandResponseDto;
	}

	private String getDisplayFailureReason(AirtelResponseCode code) {
		
		String defaultReason = "Oops! server error, we are unable to credit you at the moment. Kindly contact support";
		
		if(code == null){
			return defaultReason;
		}
		
		return defaultReason;
	}
	
	private void setTopupResponse(VtuTransactionLog transactionLog, CommandResponseDto commandResponseDto) {
		
		if(commandResponseDto == null){
			return;
		}
		
		transactionLog.setResponseCode(String.valueOf(commandResponseDto.getHttpStatusCode()));
		
		commandResponseDto.getResponseCode();
		
		COMMAND commandresp = commandResponseDto.getResponse();
		
		if(commandresp == null){
			return;
		}
		
		transactionLog.setStatusId(commandresp.getTXNSTATUS() != null ? commandresp.getTXNSTATUS().toString() : null);
		transactionLog.setTxRefId(commandresp.getTXNID());
		transactionLog.setResponseMessage(commandresp.getMESSAGE());
		transactionLog.setSeqTxRefId(commandresp.getEXTREFNUM());
	}

	private String getReference(int size) {
		String ref = System.currentTimeMillis() + "" + Math.abs(UUID.randomUUID().toString().hashCode());
		
		if(ref.length() >= size){
			ref = ref.substring(0, size);
		}
		
		return ref;
	}
	
}
