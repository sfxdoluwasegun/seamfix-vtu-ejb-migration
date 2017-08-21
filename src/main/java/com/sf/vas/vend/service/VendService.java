package com.sf.vas.vend.service;

import java.math.BigDecimal;

import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sf.vas.atjpa.entities.CurrentCycleInfo;
import com.sf.vas.atjpa.entities.TopUpProfile;
import com.sf.vas.atjpa.entities.TopupHistory;
import com.sf.vas.atjpa.entities.VtuTransactionLog;
import com.sf.vas.atjpa.enums.Status;
import com.sf.vas.atjpa.enums.TransactionType;
import com.sf.vas.vend.enums.VasVendSetting;

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
		
	}
	
	public void handleFailedVending(VtuTransactionLog transactionLog){
		
		TopupHistory topupHistory = transactionLog.getTopupHistory(); 
		
		Integer failedCount = transactionLog.getFailedCount();
		
		if(failedCount == null){
			transactionLog.setFailedCount(1);
		} else {
			failedCount = failedCount + 1;
			
			transactionLog.setFailedCount(failedCount);
		}
		
		sendFailedAirtimeTransferSms(transactionLog);
		
		transactionLog.setVtuStatus(Status.FAILED);
		topupHistory.setStatus(Status.FAILED);
		
		vtuQueryService.update(transactionLog);
		vtuQueryService.update(topupHistory);

		log.info("handleFailedVending transactionLog.getVtuStatus() : "+transactionLog.getVtuStatus());
		
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
}
