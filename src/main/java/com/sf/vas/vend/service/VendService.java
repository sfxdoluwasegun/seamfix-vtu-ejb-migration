package com.sf.vas.vend.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sf.vas.atjpa.entities.ApiTxnLogs;
import com.sf.vas.atjpa.entities.ApiTxnLogs_;
import com.sf.vas.atjpa.entities.CurrentCycleInfo;
import com.sf.vas.atjpa.entities.Subscriber;
import com.sf.vas.atjpa.entities.TopUpProfile;
import com.sf.vas.atjpa.entities.TopupHistory;
import com.sf.vas.atjpa.entities.VtuTransactionLog;
import com.sf.vas.atjpa.enums.RoleTypes;
import com.sf.vas.atjpa.enums.Status;
import com.sf.vas.atjpa.enums.TransactionType;
import com.sf.vas.utils.enums.SmsProps;
import com.sf.vas.vend.enums.VasVendSetting;
import com.sf.vas.vend.restclient.ResellerVendNotification;
import com.sf.vas.vend.util.VendUtil;

/**
 * @author DAWUZI
 *
 */

@Stateless
public class VendService {
	
	@Inject
	VasVendQueryService vtuQueryService;
	
	@Inject
	VtuVasService vtuMtnService;
	
	@Inject
	VtuMtnAsyncService asyncService;
	
	@Inject
	ResellerVendNotification resellerVendNotification ;
	
	private VendUtil vendUtil = new VendUtil();
	
	private Logger log = LoggerFactory.getLogger(getClass());
	
	public void handleSuccessfulVending(VtuTransactionLog transactionLog){
		
		CurrentCycleInfo currentCycleInfo = null;
		TopupHistory topupHistory = transactionLog.getTopupHistory(); 
		
		transactionLog.setVtuStatus(Status.SUCCESSFUL);
		topupHistory.setStatus(Status.SUCCESSFUL);
		topupHistory.setFailureReason(null);
		topupHistory.setDisplayFailureReason(null);
		
		TopUpProfile topUpProfile = transactionLog.getTopUpProfile();
		
//		only auto airtime should be added to the amount for the current cycle
		if(TransactionType.AUTO.equals(topupHistory.getTransactionType()) && topUpProfile != null){
		
			currentCycleInfo = vtuMtnService.getCycleInfoCreateIfNotExist(topUpProfile);
			
			BigDecimal amount = topupHistory.getAmount();
			
			BigDecimal currentCummulativeAmount = currentCycleInfo.getCurrentCummulativeAmount(); 
			
			BigDecimal newCummulativeAmount = currentCummulativeAmount.add(amount);
			
			BigDecimal topupLimit = topUpProfile.getTopupLimit();
			
			BigDecimal newMaxAmountLeft = topupLimit.subtract(newCummulativeAmount);
			
			currentCycleInfo.setCurrentCummulativeAmount(newCummulativeAmount);
			currentCycleInfo.setMaxAmountLeft(newMaxAmountLeft);
			currentCycleInfo.setLastKnownTopupAmount(amount);
		} else {
//			this would be the case in an instant top up scenario and it is not subject to any top up limit restriction
		}
		
		vtuQueryService.update(transactionLog);
		vtuQueryService.update(topupHistory);
		
		if(currentCycleInfo != null){
			vtuQueryService.update(currentCycleInfo);
		}
		
		try {
//			invoked asynchronously but just in case
			asyncService.sendSuccessfulAirtimeTransferSms(transactionLog);
		} catch (Exception e) {
			log.error("Error sending sms", e);
		}
		
		notifyService(transactionLog.getCallBackUrl());
		
		if (transactionLog.getRoleType() != null && transactionLog.getRoleType().equals(RoleTypes.RESELLER))
			doResellerVendNotification(Status.SUCCESSFUL, Status.SUCCESSFUL.name(), topupHistory, topUpProfile);
	}
	
	/**
	 * Handle re-seller air time vend notification.
	 * 
	 * @param status transaction status
	 * @param message transaction status description
	 * @param topupHistory transaction record
	 * @param topUpProfile end user auto top-up profile
	 */
	@Asynchronous
	private void doResellerVendNotification(Status status, String message, TopupHistory topupHistory, TopUpProfile topUpProfile) {
		// TODO Auto-generated method stub
		
		ApiTxnLogs apiTxnLogs = vtuQueryService.getApiTransactionLogByReferenceNo(topupHistory.getReferenceNo(), ApiTxnLogs_.subscriber);
		if (apiTxnLogs == null)
			return;
		
		resellerVendNotification.resellerVendNotificationClient(apiTxnLogs.getSubscriber(), topUpProfile, topupHistory, status, message);
		
		apiTxnLogs.setAmountDebited(topupHistory.getAmount());
		apiTxnLogs.setMessage(message);
		apiTxnLogs.setStatus(status);
		apiTxnLogs.setVendorFeedBackTime(Timestamp.valueOf(LocalDateTime.now()));
		
		vtuQueryService.update(apiTxnLogs);
	}

	public void handleFailedVendingWithNewTransaction(VtuTransactionLog transactionLog){
		handleFailedVendingWithNewTransaction(transactionLog, false);
	}

	@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
	public void handleFailedVendingWithNewTransaction(VtuTransactionLog transactionLog, boolean connectionError){
		log.info("handleFailedVendingWithNewTransaction called for vtu log : "+transactionLog.getPk());
		handleFailedVending(transactionLog, connectionError);
	}	
	
	public void handleFailedVending(VtuTransactionLog transactionLog){
		handleFailedVending(transactionLog, false);
	}
	
	public void handleFailedVending(VtuTransactionLog transactionLog, boolean connectionError){
		
		TopupHistory topupHistory = transactionLog.getTopupHistory(); 
		
		if(connectionError){
			
			long maxNotificationCount = vtuQueryService.getSettingValueLong(VasVendSetting.VTU_MAX_VEND_SERVICE_UNREACHEABLE_NOTIFICATION_COUNT);
			
			long countOfNotificationSentToday = getUnreacheableSmsCount(transactionLog.getSender());
			
			if(maxNotificationCount > countOfNotificationSentToday){
				sendVendUnreacheableFailedSms(transactionLog);
			} else {
				log.info("maxNotificationCount : "+maxNotificationCount+", countOfNotificationSentToday : "+countOfNotificationSentToday);
			}
			
		} else {
			
			Integer failedCount = transactionLog.getFailedCount();
			
			if(failedCount == null){
				transactionLog.setFailedCount(1);
			} else {
				failedCount = failedCount + 1;
				
				transactionLog.setFailedCount(failedCount);
			}
			
			sendFailedAirtimeTransferSms(transactionLog);
		}
		
		transactionLog.setVtuStatus(Status.FAILED);
		topupHistory.setStatus(Status.FAILED);
		
		vtuQueryService.update(transactionLog);
		vtuQueryService.update(topupHistory);

		log.info("handleFailedVending transactionLog.getVtuStatus() : "+transactionLog.getVtuStatus());
		
		notifyService(transactionLog.getCallBackUrl());
		
		if (transactionLog.getRoleType() != null && transactionLog.getRoleType().equals(RoleTypes.RESELLER))
			doResellerVendNotification(Status.FAILED, "server unable to process request at the moment", topupHistory, transactionLog.getTopUpProfile());
	}
	
	private void sendVendUnreacheableFailedSms(VtuTransactionLog transactionLog) {
		try {
			asyncService.sendVendUnreacheableFailedSms(transactionLog);
		} catch (Exception e) {
			log.error("Error sendVendUnreacheableFailedSms", e);
		}
	}

	private Long getUnreacheableSmsCount(Subscriber subscriber) {

		LocalDate today = LocalDate.now();
		LocalDate tomorrow = today.plusDays(1L);

		Timestamp start = Timestamp.valueOf(today.atStartOfDay());
		Timestamp end = Timestamp.valueOf(tomorrow.atStartOfDay());

		Long count = vtuQueryService.getCountSmsLogByTypeMsisdnAndDateRange(SmsProps.VEND_SERVICE_UNREACHEABLE_SUBSCRIBER_MSG.name(), 
				vendUtil.getSubscriberMsisdn(subscriber), start, end);

		return count != null ? count : 0;
	}
	
	/**
	 * @param transactionLog
	 */
	private void sendFailedAirtimeTransferSms(VtuTransactionLog transactionLog) {
		
		Long maxAttempt;
		
		try {
			maxAttempt = Long.valueOf(vtuQueryService.getSettingValue(VasVendSetting.VTU_FAILED_MAX_RETRIAL_ATTEMPTS));
		} catch (Exception e) {
			maxAttempt = Long.valueOf(VasVendSetting.VTU_FAILED_MAX_RETRIAL_ATTEMPTS.getDefaultValue());
		}
		
		Integer failedCount = transactionLog.getFailedCount();
		
		if(failedCount == null){
			failedCount = 1;
		}
		
		if(failedCount < maxAttempt){
			log.info("skipping sending the failed sms because max attempts not reached yet. current retrial count : "+failedCount+", max attempt : "+maxAttempt);
			return;
		}
		
		try {
//			invoked asynchronously but just in case
			asyncService.sendFailedAirtimeTransferSms(transactionLog);
		} catch (Exception e) {
			log.error("Error sending sms", e);
		}
	}
	
	public boolean notifyService(String url){
		
		if(url == null || url.trim().isEmpty()){
			log.info("url is null or empty : -"+url+"-");
			return false;
		}
		
		try {
			
			log.info("Sending signal to : "+url);
			
			Client client = ClientBuilder.newClient();
			
			Response response = client.target(url).request().get();
			
			if(response == null){
				log.info("response is null");
				return false;
			} else {
				log.info("response status : "+response.getStatus()); 
				return (Response.Status.OK.getStatusCode() == response.getStatus());
			}
			
		} catch (Exception e) {
			log.error(" XXXXXXXXXXX \nXXXXXXXXXXXXXXXXXXXXXX  Error notifying "+url+" message : "+e.getMessage()+" XXXXXXXXXXXXXXXXXXXXXX\nXXXXXXXXXXX");
		}
		
		return false;
	}
}
