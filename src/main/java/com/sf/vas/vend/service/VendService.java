package com.sf.vas.vend.service;

import java.math.BigDecimal;

import javax.ejb.Stateless;
import javax.inject.Inject;

import com.sf.vas.atjpa.entities.CurrentCycleInfo;
import com.sf.vas.atjpa.entities.TopUpProfile;
import com.sf.vas.atjpa.entities.TopupHistory;
import com.sf.vas.atjpa.entities.VtuTransactionLog;
import com.sf.vas.atjpa.enums.Status;
import com.sf.vas.atjpa.enums.TransactionType;
import com.sf.vas.mtnvtu.service.VtuMtnService;
import com.sf.vas.mtnvtu.tools.VtuMtnQueryService;

/**
 * @author DAWUZI
 *
 */

@Stateless
public class VendService {
	
	@Inject
	VtuMtnQueryService vtuQueryService;
	
	@Inject
	VtuMtnService vtuMtnService;

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
		
//		sendFailedAirtimeTransferSms(transactionLog);
		
		transactionLog.setVtuStatus(Status.FAILED);
		topupHistory.setStatus(Status.FAILED);
		
		vtuQueryService.update(transactionLog);
		vtuQueryService.update(topupHistory);
	}
	
}
