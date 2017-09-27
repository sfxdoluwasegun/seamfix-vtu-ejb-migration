package com.sf.vas.vend.wrappers;

import java.net.ConnectException;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.ejb.EJBTransactionRolledbackException;
import javax.ejb.Stateless;
import javax.inject.Inject;
import com.sf.vas.airtimevend.glo.dto.GloVendInitParams;
import com.sf.vas.airtimevend.glo.dto.RequestTopupExtraParams;
import com.sf.vas.airtimevend.glo.dto.RequestTopupParams;
import com.sf.vas.airtimevend.glo.dto.RequestTopupResponseDto;
import com.sf.vas.airtimevend.glo.enums.GloServiceResponseCode;
import com.sf.vas.airtimevend.glo.enums.RequestTopupType;
import com.sf.vas.airtimevend.glo.service.GloService;
import com.sf.vas.airtimevend.glo.soapartifacts.Amount;
import com.sf.vas.airtimevend.glo.soapartifacts.ERSWSTopupResponse;
import com.sf.vas.airtimevend.glo.soapartifacts.Principal;
import com.sf.vas.airtimevend.glo.soapartifacts.RequestTopupResponse;
import com.sf.vas.atjpa.entities.TopupHistory;
import com.sf.vas.atjpa.entities.VtuTransactionLog;
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
import com.sf.vas.vend.util.VendUtil;

/**
 * @author DAWUZI
 *
 */

@Stateless
public class GloNgVendWrapperService extends AbstractAirtimeTransferHandler {

	private GloService gloService;
	
	@Inject
	private VasVendQueryService vtuQueryService;
	
	@Inject
	VendService vendService;
	
	@Inject
	private EncryptionUtil encryptionUtil;
	
	private RequestTopupExtraParams defaultExtraParams = new RequestTopupExtraParams();
	
	private final String DEFAULT_ORIGINATOR_MSISDN = "DEFAULT";
	private final String DEFAULT_NOT_APPLICABLE = "NA";
	
	@PostConstruct
	private void init(){
		
		String sPassword = vtuQueryService.getSettingValue(VasVendSetting.GLO_NG_SERVICE_PASSWORD);
		
		if(sPassword == null || sPassword.trim().isEmpty()){
			throw new VasRuntimeException("glo ng service password not configured"); 
		}
		
		String password;
		try {
			password = encryptionUtil.decrypt(sPassword);
		} catch (VasException e) {
			throw new VasRuntimeException("error decrypting configured password", e);
		}
		
		String serviceUrl = vtuQueryService.getSettingValue(VasVendSetting.GLO_NG_SERVICE_URL);
		String senderPrincipalId = vtuQueryService.getSettingValue(VasVendSetting.GLO_NG_SENDER_PRINCIPAL_ID);
		String senderPrincipalUserId = vtuQueryService.getSettingValue(VasVendSetting.GLO_NG_SENDER_PRINCIPAL_USER_ID);
		
		GloVendInitParams initParams = new GloVendInitParams();
		
		initParams.setPassword(password);
		initParams.setSenderPrincipalId(senderPrincipalId);
		initParams.setSenderPrincipalUserId(senderPrincipalUserId);
		initParams.setServiceUrl(serviceUrl);
		
		gloService = new GloService(initParams);
	}
	
	@Override
	public AirtimeTransferResponse handleTransferAirtime(AirtimeTransferRequestDTO request) {

		String ref = "GLO-NG-"+UUID.randomUUID().toString();
		GloVendInitParams initParams = gloService.getInitParams();
		
		String senderPrincipalId = initParams.getSenderPrincipalId(); // eg WEB7056670256
		
		String productId = "TOPUP";
		RequestTopupType requestTopupType = RequestTopupType.AIRTIME;
		
		VtuTransactionLog transactionLog;
		
		if(request.getVtuTransactionLog() != null){
			transactionLog = request.getVtuTransactionLog();
		} else {
			transactionLog = new VtuTransactionLog();
		}
		
		transactionLog.setAmount(request.getAmount());
		transactionLog.setCallBackUrl(request.getCallbackUrl());
		transactionLog.setClientReference(ref);
		transactionLog.setOriginatorMsisdn(DEFAULT_ORIGINATOR_MSISDN);
		transactionLog.setDestinationMsisdn(request.getMsisdn());
		transactionLog.setProductId(productId);
		transactionLog.setRoleType(request.getRoleType());
		transactionLog.setSender(request.getSubscriber());
		transactionLog.setSenderPrincipalId(senderPrincipalId); 
		transactionLog.setTariffTypeId(DEFAULT_NOT_APPLICABLE);
		transactionLog.setTopupHistory(request.getTopupHistory()); 
		transactionLog.setTopUpProfile(request.getTopUpProfile()); 
		transactionLog.setTopupType(requestTopupType.getValue());
		transactionLog.setServiceProviderId(DEFAULT_NOT_APPLICABLE);
		transactionLog.setNetworkCarrier(request.getNetworkCarrier());
		transactionLog.setVtuStatus(Status.PENDING);
		
		if(transactionLog.getPk() != null){
			vtuQueryService.update(transactionLog);
			log.info("after updating the txn log"); 
		} else {
			vtuQueryService.createImmediately(transactionLog);
		}
		
		RequestTopupParams params = new RequestTopupParams();
		
		params.setAmount(request.getAmount());
		params.setMsisdn(request.getMsisdn());
		params.setProductId(productId);
		params.setRequestTopupType(requestTopupType);
		params.setTransactionReference(ref);
		
		RequestTopupResponseDto requestTopupResponseDto = null;
		GloServiceResponseCode gloServiceResponseCode = null;
		
		Exception vendException = null;
		
		try {
			requestTopupResponseDto = gloService.requestTopup(params, defaultExtraParams);
		} catch (Exception e) {
			log.error("error calling glo service", e);
			vendException = e;
		}
		
		TopupHistory topupHistory = transactionLog.getTopupHistory();
		
		setTopupResponse(transactionLog, requestTopupResponseDto);
		
		if(requestTopupResponseDto == null){
			
			topupHistory.setFailureReason("GLO-NG VEND ERROR");
			topupHistory.setDisplayFailureReason(getDisplayFailureReason(gloServiceResponseCode));
			
			boolean isRollbackCausedException = vendUtil.isThrowableClassInStrackTrace(vendException, EJBTransactionRolledbackException.class);
			boolean connectionException = vendUtil.isThrowableClassInStrackTrace(vendException, ConnectException.class);
			
			if(isRollbackCausedException){
				vendService.handleFailedVendingWithNewTransaction(transactionLog, connectionException);
			} else {
				vendService.handleFailedVending(transactionLog, connectionException);
			}
		} else {
			
			gloServiceResponseCode = requestTopupResponseDto.getServiceResponseCode();
			
			log.info("requestTopupResponseDto : "+requestTopupResponseDto);
			
			if(gloServiceResponseCode != null && GloServiceResponseCode.SUCCESS.equals(gloServiceResponseCode)){
				vendService.handleSuccessfulVending(transactionLog);
			} else {
				
				if(gloServiceResponseCode != null){
					topupHistory.setFailureReason("GLO-NG VEND "+gloServiceResponseCode.name());
				} else {
					topupHistory.setFailureReason("GLO-NG VEND ERROR");
				}
				topupHistory.setDisplayFailureReason(getDisplayFailureReason(gloServiceResponseCode));
				
				vendService.handleFailedVending(transactionLog);
			}
		}
		
		AirtimeTransferResponse response = new AirtimeTransferResponse();
		
		response.assignResponseCode(ResponseCode.SUCCESS);
		response.setTransactionId(transactionLog.getPk());
		
		return response;
	}

	private String getDisplayFailureReason(GloServiceResponseCode code) {
		
		String defaultReason = "Oops! server error, we are unable to credit you at the moment. Kindly contact support";
		
		if(code == null){
			return defaultReason;
		}
		
		return defaultReason;
	}

	private void setTopupResponse(VtuTransactionLog transactionLog, RequestTopupResponseDto requestTopupResponseDto) {
		
		if(requestTopupResponseDto == null 
				|| requestTopupResponseDto.getRequestTopupResponse() == null 
				|| requestTopupResponseDto.getRequestTopupResponse().getReturn() == null){
			return;
		}
		
		ERSWSTopupResponse erswsTopupResponse = requestTopupResponseDto.getRequestTopupResponse().getReturn();
		
		String ersReference = erswsTopupResponse.getErsReference();
		String responseCode = String.valueOf(erswsTopupResponse.getResultCode());
		String resultDescription = erswsTopupResponse.getResultDescription();
		String balance = null;
		String msisdn = DEFAULT_ORIGINATOR_MSISDN;
		
		Principal senderPrincipal = erswsTopupResponse.getSenderPrincipal();
		
		if(senderPrincipal != null){
			
			if(senderPrincipal.getAccounts() != null 
					&& senderPrincipal.getAccounts().getAccount() != null
					&& !senderPrincipal.getAccounts().getAccount().isEmpty()){
				
				Amount respBalance = senderPrincipal.getAccounts().getAccount().get(0).getBalance();
				if(respBalance != null){
					balance = respBalance.getValue().toPlainString();
				}
			}
			
			if(senderPrincipal.getMsisdn() != null){
				msisdn = senderPrincipal.getMsisdn();
			}
		}
		
		transactionLog.setStatusId(responseCode);
		transactionLog.setTxRefId(ersReference);
		transactionLog.setOrigBalance(balance);
		transactionLog.setOriginatorMsisdn(msisdn);
		transactionLog.setResponseCode(responseCode);
		transactionLog.setResponseMessage(resultDescription);
	}

	public static void main(String[] args) {
		RequestTopupResponseDto responseDto = new RequestTopupResponseDto(new RequestTopupResponse());
		
		System.out.println(responseDto);
	}
}
