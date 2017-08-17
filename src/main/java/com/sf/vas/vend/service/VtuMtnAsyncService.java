/**
 * 
 */
package com.sf.vas.vend.service;

import java.util.HashMap;
import java.util.Map;

import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sf.vas.atjpa.entities.Subscriber;
import com.sf.vas.atjpa.entities.VtuTransactionLog;
import com.sf.vas.atjpa.enums.TransactionType;
import com.sf.vas.mtnsms.service.SmsMtnService;
import com.sf.vas.utils.enums.SmsProps;
import com.sf.vas.utils.exception.VasException;

/**
 * @author dawuzi
 *
 */
@Stateless
public class VtuMtnAsyncService {

	private Logger log = LoggerFactory.getLogger(getClass());
	
	@Inject
	SmsMtnService smsMtnService;
	
	@Asynchronous
	public void sendSuccessfulAirtimeTransferSms(VtuTransactionLog transactionLog){
		
		Subscriber sender = transactionLog.getSender();
		
		String recipientMsisdn = transactionLog.getDestinationMsisdn();
		String subscriberMsisdn = sender.getPhoneNumber();
		String amount = String.valueOf(transactionLog.getAmount().intValue());
		String subscriberFullName = sender.getLastName()+" "+sender.getFirstName();
		String subscriberName = sender.getFirstName();
		
		if(recipientMsisdn.startsWith("+")){
			recipientMsisdn = recipientMsisdn.substring(1);
		}
		if(subscriberMsisdn.startsWith("+")){
			subscriberMsisdn = subscriberMsisdn.substring(1);
		}
		
		boolean sameUser = recipientMsisdn.equalsIgnoreCase(subscriberMsisdn);
		
		Map<String, Object> params = new HashMap<>();
		
		params.put("subscriberName", subscriberName);
		params.put("number", recipientMsisdn);
		params.put("amount", amount);
		params.put("subscriberFullName", subscriberFullName);
		
		TransactionType transactionType = transactionLog.getTopupHistory().getTransactionType();
		
		switch (transactionType) {
		
		case AUTO:
			sendSms(SmsProps.AUTO_TOPUP_SUCCESSFUL_SUBSCRIBER, subscriberMsisdn, params);
			if(!sameUser){
				sendSms(SmsProps.AUTO_TOPUP_SUCCESSFUL_RECIPIENT, recipientMsisdn, params);
			}
			break;
		case ACTIVATION:
			
			break;
		case INSTANT:
			sendSms(SmsProps.INSTANT_TOPUP_SUCCESSFUL_SUBSCRIBER, subscriberMsisdn, params);
			if(!sameUser){
				sendSms(SmsProps.INSTANT_TOPUP_SUCCESSFUL_RECIPIENT, recipientMsisdn, params);
			}
			break;
		case SIGNUP_BONUS:
			sendSms(SmsProps.BONUS_AIRTIME_SUCCESS, subscriberMsisdn, params);
			break;
			
		case SCHEDULED:
			sendSms(SmsProps.SCHEDULED_TOPUP_SUCCESSFUL_SUBSCRIBER, subscriberMsisdn, params);
			if(!sameUser){
				sendSms(SmsProps.SCHEDULED_TOPUP_SUCCESSFUL_RECIPIENT, recipientMsisdn, params);
			}
			break;
			
		case REFERRAL:
			sendSms(SmsProps.REFERRAL_TOPUP_SUCCESSFUL_SUBSCRIBER, subscriberMsisdn, params);
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
	public void sendSms(SmsProps smsProps, String msisdn, String param, String value) {
		try {
			smsMtnService.sendSms(smsProps, msisdn, param, value);
		} catch (VasException e) {
			log.error("VasException", e);
		}
	}
	@Asynchronous
	public void sendSms(SmsProps smsProps, String msisdn, Map<String, Object> params) {
		try {
			smsMtnService.sendSms(smsProps, msisdn, params);
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
		
		String recipientMsisdn = transactionLog.getDestinationMsisdn();
		String subscriberMsisdn = sender.getPhoneNumber();
		String subscriberName = sender.getFirstName();
		
		if(recipientMsisdn.startsWith("+")){
			recipientMsisdn = recipientMsisdn.substring(1);
		}
		if(subscriberMsisdn.startsWith("+")){
			subscriberMsisdn = subscriberMsisdn.substring(1);
		}
		
		boolean sameUser = recipientMsisdn.equalsIgnoreCase(subscriberMsisdn);
		
		Map<String, Object> params = new HashMap<>();
		
		params.put("subscriberName", subscriberName);
		params.put("number", recipientMsisdn);
		params.put("amount", transactionLog.getAmount().intValue());
		params.put("reason", "server unable to process request at the moment");
		
		TransactionType transactionType = transactionLog.getTopupHistory().getTransactionType();
		
		transactionType = TransactionType.AUTO;
		
		switch (transactionType) {
		
		case AUTO:
			sendSms(SmsProps.AUTO_TOPUP_FAILED_SUBSCRIBER, subscriberMsisdn, params);
			break;
		case ACTIVATION:
			
			break;
		case INSTANT:
			sendSms(SmsProps.INSTANT_TOPUP_FAILED_SUBSCRIBER, subscriberMsisdn, params);
			break;
		case SIGNUP_BONUS:
			sendSms(SmsProps.BONUS_AIRTIME_ERROR, subscriberMsisdn, params);
			break;
			
		case SCHEDULED:
			sendSms(SmsProps.SCHEDULED_TOPUP_FAILED_SUBSCRIBER, subscriberMsisdn, params);
			if(!sameUser){
				sendSms(SmsProps.SCHEDULED_TOPUP_FAILED_RECIPIENT, recipientMsisdn, params);
			}
			break;
			
		case REFERRAL:
			sendSms(SmsProps.REFERRAL_TOPUP_FAILED_SUBSCRIBER, subscriberMsisdn, params);
			break;

		default:
			break;
		}
		
	}
}
