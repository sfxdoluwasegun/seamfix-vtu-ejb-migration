package com.sf.vas.vend.wrappers;

import java.util.List;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.jms.JMSException;

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
import com.sf.vas.airtimevend.glo.soapartifacts.Principal.Accounts;
import com.sf.vas.airtimevend.glo.soapartifacts.PrincipalAccount;
import com.sf.vas.airtimevend.glo.soapartifacts.RequestTopupResponse;
import com.sf.vas.airtimevend.mtn.enums.MtnVtuVendStatusCode;
import com.sf.vas.atjpa.entities.TopupHistory;
import com.sf.vas.atjpa.entities.VtuTransactionLog;
import com.sf.vas.atjpa.enums.Status;
import com.sf.vas.mtnvtu.dto.AirtimeTransferRequestDTO;
import com.sf.vas.mtnvtu.enums.ResponseCode;
import com.sf.vas.mtnvtu.enums.VtuMtnSetting;
import com.sf.vas.mtnvtu.service.VtuMtnAsyncService;
import com.sf.vas.mtnvtu.tools.VtuMtnQueryService;
import com.sf.vas.utils.crypto.EncryptionUtil;
import com.sf.vas.utils.exception.VasException;
import com.sf.vas.utils.exception.VasRuntimeException;
import com.sf.vas.utils.restartifacts.vtu.AirtimeTransferResponse;
import com.sf.vas.vend.service.VendService;
import com.sf.vas.vtu.IAirtimeTransferHandler;

/**
 * @author DAWUZI
 *
 */

@Stateless
public class GloNgVendWrapperService extends IAirtimeTransferHandler {

	private GloService gloService;
	
	@Inject
	private VtuMtnQueryService vtuQueryService;
	
	@Inject
	VtuMtnAsyncService asyncService;
	
	@Inject
	VendService vendService;
	
	@Inject
	private EncryptionUtil encryptionUtil;
	
	private RequestTopupExtraParams defaultExtraParams = new RequestTopupExtraParams();
	
	@PostConstruct
	private void init(){
		
		String sPassword = vtuQueryService.getSettingValue(VtuMtnSetting.GLO_NG_SERVICE_PASSWORD);
		
		if(sPassword == null || sPassword.trim().isEmpty()){
			throw new VasRuntimeException("glo ng service password not configured"); 
		}
		
		String password;
		try {
			password = encryptionUtil.decrypt(sPassword);
		} catch (VasException e) {
			throw new VasRuntimeException("error decrypting configured password", e);
		}
		
		String serviceUrl = vtuQueryService.getSettingValue(VtuMtnSetting.GLO_NG_SERVICE_URL);
		String senderPrincipalId = vtuQueryService.getSettingValue(VtuMtnSetting.GLO_NG_SENDER_PRINCIPAL_ID);
		String senderPrincipalUserId = vtuQueryService.getSettingValue(VtuMtnSetting.GLO_NG_SENDER_PRINCIPAL_USER_ID);
		
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
		String senderPrincipalUserId = initParams.getSenderPrincipalUserId();
		
		String channel = defaultExtraParams.getChannel();
		String clientId = defaultExtraParams.getClientId();
		String topupPrincipalIdType = defaultExtraParams.getTopupPrincipalIdType();
		String productId = "TOPUP";
		RequestTopupType requestTopupType = RequestTopupType.AIRTIME;
		
		VtuTransactionLog transactionLog = new VtuTransactionLog();
		
		transactionLog.setAmount(request.getAmount());
		transactionLog.setCallBackUrl(request.getCallbackUrl());
		transactionLog.setOriginatorMsisdn(senderPrincipalId);
		transactionLog.setDestinationMsisdn(request.getMsisdn());
		transactionLog.setSender(request.getSubscriber());
		transactionLog.setTariffTypeId(requestTopupType.name());
		transactionLog.setTopupHistory(request.getTopupHistory()); 
		transactionLog.setTopUpProfile(request.getTopUpProfile()); 
		transactionLog.setSeqStatus(ref);
		transactionLog.setSeqTxRefId(clientId);
		transactionLog.setServiceProviderId(channel);
		transactionLog.setNetworkCarrier(request.getNetworkCarrier());
		transactionLog.setVtuStatus(Status.PENDING);
		
		vtuQueryService.createImmediately(transactionLog);
 		
		RequestTopupParams params = new RequestTopupParams();
		
		params.setAmount(request.getAmount());
		params.setMsisdn(request.getMsisdn());
		params.setProductId(productId);
		params.setRequestTopupType(requestTopupType);
		params.setTransactionReference(ref);
		
		RequestTopupResponseDto requestTopupResponseDto = null;
		
		try {
			requestTopupResponseDto = gloService.requestTopup(params, defaultExtraParams);
		} catch (Exception e) {
			log.error("error calling glo service", e);
		}
		
		setTopupResponse(transactionLog, requestTopupResponseDto);
		
		GloServiceResponseCode gloServiceResponseCode = null;
		
		if(requestTopupResponseDto != null){
			gloServiceResponseCode = requestTopupResponseDto.getServiceResponseCode();
		}
		
		log.info("requestTopupResponseDto : "+requestTopupResponseDto);
		
		if(gloServiceResponseCode != null && GloServiceResponseCode.SUCCESS.equals(gloServiceResponseCode)){
			vendService.handleSuccessfulVending(transactionLog);
		} else {
			
			TopupHistory topupHistory = transactionLog.getTopupHistory();
			
			if(gloServiceResponseCode != null){
				topupHistory.setFailureReason("GLO-NG VEND "+gloServiceResponseCode.name());
			} else {
				topupHistory.setFailureReason("GLO-NG VEND ERROR");
			}
			topupHistory.setDisplayFailureReason(getDisplayFailureReason(gloServiceResponseCode));
			
			vendService.handleFailedVending(transactionLog);
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
		String msisdn = null;
		
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
			
			msisdn = senderPrincipal.getMsisdn();
		}
		
		transactionLog.setStatusId(responseCode);
		transactionLog.setTxRefId(ersReference);
//		transactionLog.setSeqStatus(vendResponse.getSeqstatus());
//		transactionLog.setSeqTxRefId(msisdn);
		transactionLog.setLastSeq(msisdn);
		transactionLog.setOrigBalance(balance);
//		transactionLog.setDestBalance(vendResponse.getDestBalance());
		transactionLog.setResponseCode(responseCode);
		transactionLog.setResponseMessage(resultDescription);
	}

	public static void main(String[] args) {
		RequestTopupResponseDto responseDto = new RequestTopupResponseDto(new RequestTopupResponse());
		
		System.out.println(responseDto);
	}
}
