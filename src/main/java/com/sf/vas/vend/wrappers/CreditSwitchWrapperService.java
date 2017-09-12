package com.sf.vas.vend.wrappers;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.ejb.Stateless;
import javax.inject.Inject;

import com.sf.vas.airtimevend.credit_switch.dto.CreditSwitchInitParams;
import com.sf.vas.airtimevend.credit_switch.dto.CreditSwitchVendResponseDto;
import com.sf.vas.airtimevend.credit_switch.dto.CsVendRequest;
import com.sf.vas.airtimevend.credit_switch.enums.CreditSwitchResponseCode;
import com.sf.vas.airtimevend.credit_switch.enums.CsNetwork;
import com.sf.vas.airtimevend.credit_switch.restartifacts.mvend.CreditSwitchVendResponse;
import com.sf.vas.airtimevend.credit_switch.service.CreditSwitchService;
import com.sf.vas.airtimevend.pojos.RestServiceResponse;
import com.sf.vas.atjpa.entities.TopupHistory;
import com.sf.vas.atjpa.entities.VtuTransactionLog;
import com.sf.vas.atjpa.enums.NetworkCarrierType;
import com.sf.vas.atjpa.enums.Status;
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
public class CreditSwitchWrapperService extends IAirtimeTransferHandler {

	private CreditSwitchService creditSwitchService;
	
	@Inject
	VendService vendService;
	
	@Inject
	private VasVendQueryService queryService;

	@Inject
	private EncryptionUtil encryptionUtil;
	
	private Pattern HyphenPattern = Pattern.compile("-");
	
	private final String DEFAULT_NOT_APPLICABLE = "NA";
	
	@PostConstruct
	private void init(){
		
		String initPath = queryService.getSettingValue(VasVendSetting.CREDIT_SWITCH_INIT_PATH);
		Integer loginId = queryService.getSettingValueInt(VasVendSetting.CREDIT_SWITCH_LOGIN_ID);
		String encPublicKey = queryService.getSettingValue(VasVendSetting.CREDIT_SWITCH_PUBLIC_KEY);
		String encPrivateKey = queryService.getSettingValue(VasVendSetting.CREDIT_SWITCH_PRIVATE_KEY);
		
		String publicKey;
		String privateKey;
		
		try {
			publicKey = encryptionUtil.decrypt(encPublicKey);
			privateKey = encryptionUtil.decrypt(encPrivateKey);
		} catch (VasException e) {
			throw new VasRuntimeException("error decrypting configured credit switch public/private keys", e);
		}
		
		if(publicKey == null || publicKey.trim().isEmpty() || privateKey == null || privateKey.trim().isEmpty()){
			throw new VasRuntimeException("credit switch public/private keys are null or empty after decrypting");
		}
		
		CreditSwitchInitParams initParams = new CreditSwitchInitParams();
		
		initParams.setEndPointInitPath(initPath);
		initParams.setLoginId(loginId);
		initParams.setPrivateKey(privateKey);
		initParams.setPublicKey(publicKey);
		
		creditSwitchService = new CreditSwitchService(initParams);
	}	
	
	@Override
	public AirtimeTransferResponse handleTransferAirtime(AirtimeTransferRequestDTO request) {

		String reference = HyphenPattern.matcher(UUID.randomUUID().toString()).replaceAll("");
		
		CsNetwork csNetwork = getCsNetwork(request.getNetworkCarrier().getType());
		String serviceId = "UNKNOWN";
		
		if(csNetwork != null){
			serviceId = csNetwork.getServiceCode();
		}
		
		VtuTransactionLog transactionLog;
		
		if(request.getVtuTransactionLog() != null){
			transactionLog = request.getVtuTransactionLog();
		} else {
			transactionLog = new VtuTransactionLog();
		}
		
		transactionLog.setAmount(request.getAmount());
		transactionLog.setCallBackUrl(request.getCallbackUrl());
		transactionLog.setClientReference(reference);
		transactionLog.setOriginatorMsisdn(DEFAULT_NOT_APPLICABLE);
		transactionLog.setDestinationMsisdn(request.getMsisdn());
		transactionLog.setSender(request.getSubscriber());
		transactionLog.setTariffTypeId(DEFAULT_NOT_APPLICABLE);
		transactionLog.setTopupHistory(request.getTopupHistory()); 
		transactionLog.setTopUpProfile(request.getTopUpProfile()); 
		transactionLog.setServiceProviderId(serviceId);
		transactionLog.setNetworkCarrier(request.getNetworkCarrier());
		transactionLog.setVtuStatus(Status.PENDING);
		
		if(transactionLog.getPk() != null){
			queryService.update(transactionLog);
			log.info("after updating the txn log"); 
		} else {
			queryService.createImmediately(transactionLog);
		}
		
		CsVendRequest vendRequest = new CsVendRequest();
		
		vendRequest.setAmount(request.getAmount());
		vendRequest.setCsNetwork(csNetwork);
		vendRequest.setMsisdn(request.getMsisdn());
		vendRequest.setReference(reference);
		
		CreditSwitchVendResponseDto creditSwitchVendResponseDto = null;
		
		try {
			creditSwitchVendResponseDto = creditSwitchService.sendAirtimeVendRequest(vendRequest);
		} catch (Exception e) {
			log.error("Error sending airtime request through credit switch", e);
		}
		
		setResponse(transactionLog, creditSwitchVendResponseDto);
		
		CreditSwitchResponseCode responseCode = null;
		
		if(creditSwitchVendResponseDto != null){
			responseCode = creditSwitchVendResponseDto.getResponseCode();
		}
		
		if(responseCode != null && CreditSwitchResponseCode.SUCCESS.equals(responseCode)){
			vendService.handleSuccessfulVending(transactionLog);
		} else {
			
			TopupHistory topupHistory = transactionLog.getTopupHistory();
			
			if(responseCode != null){
				topupHistory.setFailureReason("CREDIT SWITCH VEND "+responseCode.name());
			} else {
				topupHistory.setFailureReason("CREDIT SWITCH VEND ERROR");
			}
			topupHistory.setDisplayFailureReason(getDisplayFailureReason(responseCode));
			
			notifySupport(responseCode, request);
			
			vendService.handleFailedVending(transactionLog);
		}
		
		AirtimeTransferResponse response = new AirtimeTransferResponse();
		
		response.assignResponseCode(ResponseCode.SUCCESS);
		response.setTransactionId(transactionLog.getPk());
		
		return response;
	}

	private void notifySupport(CreditSwitchResponseCode responseCode, AirtimeTransferRequestDTO request) {
		// TODO Auto-generated method stub
		
	}

	private String getDisplayFailureReason(CreditSwitchResponseCode responseCode) {
		
		String defaultReason = "Oops! server error, we are unable to credit you at the moment. Kindly contact support";
		
		if(responseCode == null){
			return defaultReason;
		}
		
		switch (responseCode) {
		case PROVIDER_SIDE_PLATFORM_ERROR:
			return "Oops ! Could not transfer airtime. Reason : Invalid network carrier specified";
		case PROVIDER_SIDE_INVALID_SUBSCRIBER_NUMBER:
			return "Oops ! Could not transfer airtime. Reason : Invalid phone number specified";
		default:
			return defaultReason;
		}
	}

	private void setResponse(VtuTransactionLog transactionLog, CreditSwitchVendResponseDto creditSwitchVendResponseDto) {
		
		if(creditSwitchVendResponseDto == null){
			log.warn("creditSwitchVendResponseDto is null");
			return;
		}
		
		RestServiceResponse<CreditSwitchVendResponse> restServiceResponse = creditSwitchVendResponseDto.getRestServiceResponse();
		
		if(restServiceResponse != null && restServiceResponse.getBody() != null){
			
			CreditSwitchVendResponse creditSwitchVendResponse = restServiceResponse.getBody();
			
			String confirmCode = creditSwitchVendResponse.getConfirmCode();
//			String mReference = creditSwitchVendResponse.getMReference(); // this is the client reference we sent in the request
//			String recipient = creditSwitchVendResponse.getRecipient(); 
			String statusCode = creditSwitchVendResponse.getStatusCode();
//			String tranxDate = creditSwitchVendResponse.getTranxDate(); // we already have this essentially
			String tranxReference = creditSwitchVendResponse.getTranxReference();
			Map<String, Object> additionalProperties = creditSwitchVendResponse.getAdditionalProperties();
			
			Object statusDescription = additionalProperties.get("statusDescription");
			
			transactionLog.setConfirmCode(confirmCode);
			transactionLog.setResponseCode(statusCode);
			if(statusDescription != null){
				transactionLog.setResponseMessage(statusDescription.toString());
			}
			transactionLog.setTxRefId(tranxReference);
		}
	}

	private CsNetwork getCsNetwork(NetworkCarrierType type) {
		
		if(type == null){
			return null;
		}
		
		switch (type) {
		case AIRTEL_NG:
			return CsNetwork.AIRTEL;
		case GLO_NG:
			return CsNetwork.GLO;
		case MTN_NG:
			return CsNetwork.MTN;
		case NINE_MOBILE:
			return CsNetwork.NINE_MOBILE;
		}
		
		return null;
	}
}
