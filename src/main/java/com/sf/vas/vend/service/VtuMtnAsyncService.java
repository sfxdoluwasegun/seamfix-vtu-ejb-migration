/**
 * 
 */
package com.sf.vas.vend.service;

import java.util.Map;

import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sf.sms.dto.SmsRequestDTO;
import com.sf.vas.atjpa.entities.NetworkCarrier;
import com.sf.vas.atjpa.entities.Subscriber;
import com.sf.vas.atjpa.entities.VtuTransactionLog;
import com.sf.vas.atjpa.enums.NetworkCarrierType;
import com.sf.vas.atjpa.enums.TransactionType;
import com.sf.vas.mtnsms.service.SmsMtnService;
import com.sf.vas.utils.enums.SmsProps;
import com.sf.vas.utils.exception.VasException;
import com.sf.vas.vend.util.VendUtil;

/**
 * @author dawuzi
 *
 */
@Stateless
public class VtuMtnAsyncService {

	private Logger log = LoggerFactory.getLogger(getClass());
	
	@Inject
	SmsMtnService smsMtnService;
	
	@Inject
	VasVendQueryService queryService;
	
	private VendUtil vendUtil = new VendUtil();
	
	@Asynchronous
	public void sendSuccessfulAirtimeTransferSms(VtuTransactionLog transactionLog){
		
		Subscriber sender = transactionLog.getSender();
		
		String recipientMsisdn = vendUtil.getSubscriberMsisdn(transactionLog.getDestinationMsisdn());
		String subscriberMsisdn = vendUtil.getSubscriberMsisdn(sender.getPhoneNumber());
		
		boolean sameUser = recipientMsisdn.equalsIgnoreCase(subscriberMsisdn);
		
		Map<String, Object> params = vendUtil.getVtuTransactionLogSmsParams(transactionLog);
		
		TransactionType transactionType = transactionLog.getTopupHistory().getTransactionType();
		
		NetworkCarrierType recipientCarrierType = queryService.getNetworkCarrierTypeByVtuTxnLog(transactionLog.getPk());
		
		if(recipientCarrierType == null){
			recipientCarrierType = NetworkCarrierType.MTN_NG;
		}
		
		NetworkCarrierType subscriberCarrierType;
		
		if(sameUser){
			subscriberCarrierType = recipientCarrierType;
		} else {
			
			NetworkCarrier subscriberNetworkCarrier = queryService.getSubscriberNetworkCarrier(sender);
			
			if(subscriberNetworkCarrier != null){
				subscriberCarrierType = subscriberNetworkCarrier.getType();
			} else {
				subscriberCarrierType = NetworkCarrierType.MTN_NG;
			}
		}
		
		switch (transactionType) {
		
		case AUTO:
			sendSms(SmsProps.AUTO_TOPUP_SUCCESSFUL_SUBSCRIBER, subscriberMsisdn, params, subscriberCarrierType);
			if(!sameUser){
				sendSms(SmsProps.AUTO_TOPUP_SUCCESSFUL_RECIPIENT, recipientMsisdn, params, recipientCarrierType);
			}
			break;
		case ACTIVATION:
			
			break;
		case INSTANT:
			sendSms(SmsProps.INSTANT_TOPUP_SUCCESSFUL_SUBSCRIBER, subscriberMsisdn, params, subscriberCarrierType);
			if(!sameUser){
				sendSms(SmsProps.INSTANT_TOPUP_SUCCESSFUL_RECIPIENT, recipientMsisdn, params, recipientCarrierType);
			}
			break;
		case SIGNUP_BONUS:
			sendSms(SmsProps.BONUS_AIRTIME_SUCCESS, subscriberMsisdn, params, subscriberCarrierType);
			break;
			
		case SCHEDULED:
			sendSms(SmsProps.SCHEDULED_TOPUP_SUCCESSFUL_SUBSCRIBER, subscriberMsisdn, params, subscriberCarrierType);
			if(!sameUser){
				sendSms(SmsProps.SCHEDULED_TOPUP_SUCCESSFUL_RECIPIENT, recipientMsisdn, params, recipientCarrierType);
			}
			break;
			
		case REFERRAL:
			sendSms(SmsProps.REFERRAL_TOPUP_SUCCESSFUL_SUBSCRIBER, subscriberMsisdn, params, subscriberCarrierType);
			break;
			
		default:
			break;
		}
	}
	
	/**
	 * 
	 * @param smsProps
	 * @param key
	 * @param value
	 */
	@Asynchronous
	public void sendSms(SmsProps smsProps, String msisdn, String param, String value, NetworkCarrierType networkCarrierType) {
		try {
			smsMtnService.sendSms(smsProps, msisdn, param, value, networkCarrierType);
		} catch (VasException e) {
			log.error("VasException", e);
		}
	}
	@Asynchronous
	public void sendSms(SmsProps smsProps, String msisdn, Map<String, Object> params, NetworkCarrierType networkCarrierType) {
		try {
			SmsRequestDTO smsRequestDTO = new SmsRequestDTO();
			
			smsRequestDTO.setMsisdn(msisdn);
			smsRequestDTO.setNetworkCarrierType(networkCarrierType);
			smsRequestDTO.setParams(params);
			smsRequestDTO.setSmsProps(smsProps);
			
			smsMtnService.sendSms(smsRequestDTO);
		} catch (VasException e) {
			log.error("VasException", e);
		}
	}

	/**
	 * @param transactionLog
	 */
	@Asynchronous
	public void sendFailedAirtimeTransferSms(VtuTransactionLog transactionLog) {

		Subscriber sender = transactionLog.getSender();
		
		String recipientMsisdn = vendUtil.getSubscriberMsisdn(transactionLog.getDestinationMsisdn());
		String subscriberMsisdn = vendUtil.getSubscriberMsisdn(sender.getPhoneNumber());
		
		boolean sameUser = recipientMsisdn.equalsIgnoreCase(subscriberMsisdn);
		
		Map<String, Object> params = vendUtil.getVtuTransactionLogSmsParams(transactionLog);
		
		params.put("reason", "server unable to process request at the moment");
		
		TransactionType transactionType = transactionLog.getTopupHistory().getTransactionType();
		
		NetworkCarrierType recipientCarrierType = queryService.getNetworkCarrierTypeByVtuTxnLog(transactionLog.getPk());
		
		if(recipientCarrierType == null){
			recipientCarrierType = NetworkCarrierType.MTN_NG;
		}
		
		NetworkCarrierType subscriberCarrierType;
		
		if(sameUser){
			subscriberCarrierType = recipientCarrierType;
		} else {
			
			NetworkCarrier subscriberNetworkCarrier = queryService.getSubscriberNetworkCarrier(sender);
			
			if(subscriberNetworkCarrier != null){
				subscriberCarrierType = subscriberNetworkCarrier.getType();
			} else {
				subscriberCarrierType = NetworkCarrierType.MTN_NG;
			}
		}
		
		switch (transactionType) {
		
		case AUTO:
			sendSms(SmsProps.AUTO_TOPUP_FAILED_SUBSCRIBER, subscriberMsisdn, params, subscriberCarrierType);
			break;
		case ACTIVATION:
			
			break;
		case INSTANT:
			sendSms(SmsProps.INSTANT_TOPUP_FAILED_SUBSCRIBER, subscriberMsisdn, params, subscriberCarrierType);
			break;
		case SIGNUP_BONUS:
			sendSms(SmsProps.BONUS_AIRTIME_ERROR, subscriberMsisdn, params, subscriberCarrierType);
			break;
			
		case SCHEDULED:
			sendSms(SmsProps.SCHEDULED_TOPUP_FAILED_SUBSCRIBER, subscriberMsisdn, params, subscriberCarrierType);
			if(!sameUser){
				sendSms(SmsProps.SCHEDULED_TOPUP_FAILED_RECIPIENT, recipientMsisdn, params, recipientCarrierType);
			}
			break;
			
		case REFERRAL:
			sendSms(SmsProps.REFERRAL_TOPUP_FAILED_SUBSCRIBER, subscriberMsisdn, params, subscriberCarrierType);
			break;

		default:
			break;
		}
	}

	@Asynchronous
	public void sendVendUnreacheableFailedSms(VtuTransactionLog transactionLog) {
		
		Map<String, Object> params = vendUtil.getVtuTransactionLogSmsParams(transactionLog);
		
		Subscriber sender = transactionLog.getSender();
		
		String recipientMsisdn = vendUtil.getSubscriberMsisdn(transactionLog.getDestinationMsisdn());
		String subscriberMsisdn = vendUtil.getSubscriberMsisdn(sender.getPhoneNumber());
		
		boolean sameUser = recipientMsisdn.equalsIgnoreCase(subscriberMsisdn);
		
		NetworkCarrierType recipientCarrierType = queryService.getNetworkCarrierTypeByVtuTxnLog(transactionLog.getPk());
		
		if(recipientCarrierType == null){
			recipientCarrierType = NetworkCarrierType.MTN_NG;
		}
		
		NetworkCarrierType subscriberCarrierType;
		
		if(sameUser){
			subscriberCarrierType = recipientCarrierType;
		} else {
			
			NetworkCarrier subscriberNetworkCarrier = queryService.getSubscriberNetworkCarrier(sender);
			
			if(subscriberNetworkCarrier != null){
				subscriberCarrierType = subscriberNetworkCarrier.getType();
			} else {
				subscriberCarrierType = NetworkCarrierType.MTN_NG;
			}
		}
		
		log.info("same user : "+sameUser+", recipientMsisdn : "+recipientMsisdn+", subscriberMsisdn : "+subscriberMsisdn);
		
		sendSms(SmsProps.VEND_SERVICE_UNREACHEABLE_SUBSCRIBER_MSG, subscriberMsisdn, params, subscriberCarrierType);
		
		if(!sameUser){
			log.info("about to send to recipient : "+recipientMsisdn);
			sendSms(SmsProps.VEND_SERVICE_UNREACHEABLE_RECIPIENT_MSG, recipientMsisdn, params, recipientCarrierType);
		}
	}
}
