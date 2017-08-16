package com.sf.vas.vend.wrappers;

import java.math.BigDecimal;

import javax.annotation.PostConstruct;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.jms.JMSException;

import com.sf.vas.airtimevend.mtn.dto.MtnVtuInitParams;
import com.sf.vas.airtimevend.mtn.dto.VendDto;
import com.sf.vas.airtimevend.mtn.dto.VendResponseDto;
import com.sf.vas.airtimevend.mtn.service.VtuMtnService;
import com.sf.vas.atjpa.entities.VtuTransactionLog;
import com.sf.vas.atjpa.enums.Status;
import com.sf.vas.mtnvtu.dto.AirtimeTransferRequestDTO;
import com.sf.vas.mtnvtu.enums.ResponseCode;
import com.sf.vas.mtnvtu.enums.VtuMtnSetting;
import com.sf.vas.mtnvtu.tools.VtuMtnJmsManager;
import com.sf.vas.mtnvtu.tools.VtuMtnQueryService;
import com.sf.vas.utils.crypto.EncryptionUtil;
import com.sf.vas.utils.exception.VasException;
import com.sf.vas.utils.exception.VasRuntimeException;
import com.sf.vas.utils.restartifacts.vtu.AirtimeTransferResponse;
import com.sf.vas.vtu.IAirtimeTransferHandler;

/**
 * @author DAWUZI
 *
 */

@Stateless
public class MtnNgVtuWrapperService extends IAirtimeTransferHandler {
	
	@Inject
	private VtuMtnQueryService vtuQueryService;
	
	private VtuMtnService mtnService;
	
	@Inject
	VtuMtnJmsManager jmsManager;
	
	@Inject
	private EncryptionUtil encryptionUtil;

	@PostConstruct
	private void init(){
		
		String sUsername = vtuQueryService.getSettingValue(VtuMtnSetting.VTU_VEND_USERNAME);
		String sPassword = vtuQueryService.getSettingValue(VtuMtnSetting.VTU_VEND_PASSWORD);
		
		if(sUsername == null || sUsername.trim().isEmpty() || sPassword == null || sPassword.trim().isEmpty()){
			throw new VasRuntimeException("vend user name or password not configured"); 
		}
		
		String username;
		String password;
		try {
			username = encryptionUtil.decrypt(sUsername);
			password = encryptionUtil.decrypt(sPassword);
		} catch (VasException e) {
			throw new VasRuntimeException("error decrypting configured vend user name or password", e);
		}
		
		String endpointUrl = vtuQueryService.getSettingValue(VtuMtnSetting.VTU_SERVICE_URL);
		
		MtnVtuInitParams params = new MtnVtuInitParams();
		
		params.setServiceUrl(endpointUrl);
		params.setVendPassword(password);
		params.setVendUsername(username); 
		
		mtnService = new VtuMtnService(params);
		
	}

	@Override
	public AirtimeTransferResponse handleTransferAirtime(AirtimeTransferRequestDTO request) {
		
		String originMsisdn = vtuQueryService.getSettingValue(VtuMtnSetting.VTU_ORIGINATOR_MSISDN);
		String serviceProviderId = vtuQueryService.getSettingValue(VtuMtnSetting.VTU_SERVICE_PROVIDER_ID);
		
		VtuTransactionLog transactionLog = new VtuTransactionLog();
		
		transactionLog.setAmount(request.getAmount());
		transactionLog.setCallBackUrl(request.getCallbackUrl());
		transactionLog.setOriginatorMsisdn(originMsisdn);
		transactionLog.setDestinationMsisdn(request.getMsisdn());
		transactionLog.setSender(request.getSubscriber());
		transactionLog.setTariffTypeId(getTariffTypeId(request.getAmount()));
		transactionLog.setTopupHistory(request.getTopupHistory()); 
		transactionLog.setTopUpProfile(request.getTopUpProfile()); 
		transactionLog.setServiceProviderId(serviceProviderId);
		transactionLog.setNetworkCarrier(request.getNetworkCarrier());
		transactionLog.setVtuStatus(Status.PENDING);
		
		vtuQueryService.createImmediately(transactionLog);
		
		try {
			jmsManager.sendVtuRequest(transactionLog);
		} catch (JMSException e) {
			throw new VasRuntimeException(e);
		}

		AirtimeTransferResponse response = new AirtimeTransferResponse();
		
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
	 * @param arg0
	 * @return
	 * @see com.sf.vas.airtimevend.mtn.service.VtuMtnService#sendVendRequest(com.sf.vas.airtimevend.mtn.dto.VendDto)
	 */
	public VendResponseDto sendVendRequest(VendDto arg0) {
		return mtnService.sendVendRequest(arg0);
	}
	
}
