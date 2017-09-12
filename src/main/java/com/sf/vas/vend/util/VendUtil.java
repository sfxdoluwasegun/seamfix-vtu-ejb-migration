package com.sf.vas.vend.util;

import java.util.HashMap;
import java.util.Map;

import com.sf.vas.atjpa.entities.Subscriber;
import com.sf.vas.atjpa.entities.VtuTransactionLog;

/**
 * @author DAWUZI
 *
 */

public class VendUtil {

	public boolean isThrowableClassInStrackTrace(Throwable localThrowable, Class<? extends Throwable> throwableClazz){
		
		while(localThrowable != null){
			if(localThrowable.getClass().isAssignableFrom(throwableClazz)){
				return true;
			}
			localThrowable = localThrowable.getCause();
		}
		
		return false;
	}
	
	public Map<String, Object> getVtuTransactionLogSmsParams(VtuTransactionLog transactionLog){
		Subscriber sender = transactionLog.getSender();
		
		String recipientMsisdn = getSubscriberMsisdn(transactionLog.getDestinationMsisdn());
		String subscriberMsisdn = getSubscriberMsisdn(sender.getPhoneNumber());
		String amount = String.valueOf(transactionLog.getAmount().intValue());
		String subscriberFullName = sender.getLastName()+" "+sender.getFirstName();
		String subscriberName = sender.getFirstName();
		
		Map<String, Object> params = new HashMap<>();
		
		params.put("number", recipientMsisdn);
		params.put("subscriberMsisdn", subscriberMsisdn);
		params.put("amount", amount);
		params.put("subscriberFullName", subscriberFullName);
		params.put("subscriberName", subscriberName);
		
		return params;
	}
	
	public String getSubscriberMsisdn(Subscriber subscriber) {
		return getSubscriberMsisdn(subscriber.getPhoneNumber());
	}
	
	public String getSubscriberMsisdn(String subscriberMsisdn){
		if(subscriberMsisdn.startsWith("+")){
			subscriberMsisdn = subscriberMsisdn.substring(1);
		}
		return subscriberMsisdn;
	}
	
	public static void main(String[] args) {
		test();
	}
	
	public static void test() {
		
		VendUtil util = new VendUtil();
		
		try {
			Integer.parseInt("ddf");
		} catch (Exception e) {
//			e.printStackTrace();
			IllegalArgumentException illegalArgumentException = new IllegalArgumentException(e);
//			illegalArgumentException.printStackTrace();
			System.out.println(util.isThrowableClassInStrackTrace(illegalArgumentException, IllegalArgumentException.class));
			System.out.println(util.isThrowableClassInStrackTrace(illegalArgumentException, IllegalStateException.class));
			System.out.println(util.isThrowableClassInStrackTrace(illegalArgumentException, NumberFormatException.class));
		}
		
	}
}
