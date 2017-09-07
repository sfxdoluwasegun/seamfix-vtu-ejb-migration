package com.sf.vas.vend.restclient;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.codec.digest.DigestUtils;
import org.jboss.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sf.vas.atjpa.entities.ApiTxnLogs;
import com.sf.vas.atjpa.entities.ApiUserDetails;
import com.sf.vas.atjpa.entities.Subscriber;
import com.sf.vas.atjpa.entities.TopUpProfile;
import com.sf.vas.atjpa.entities.TopupHistory;
import com.sf.vas.atjpa.enums.ApiTxnTypes;
import com.sf.vas.atjpa.enums.Status;
import com.sf.vas.vend.service.VasVendQueryService;

public class ResellerVendNotification {
	
	private Logger log = Logger.getLogger(getClass());
	
	@Inject
	VasVendQueryService queryService;

	/**
	 * Notify re-seller of air time vend status.
	 * 
	 * @param subscriber
	 * @param topUpProfile
	 * @param topupHistory
	 * @param status
	 * @param message
	 */
	public void resellerVendNotificationClient(Subscriber subscriber, TopUpProfile topUpProfile, TopupHistory topupHistory, Status status, String message) {

		Client client = null;
		Response response = null;
		
		ApiUserDetails apiUserDetails = queryService.getResellerApiDetailBySubscriber(subscriber);
		
		String securityToken = DigestUtils.sha512Hex(new StringBuffer(apiUserDetails.getApiKey()).append(apiUserDetails.getPk()).toString());
		
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("amount", topupHistory.getAmount());
		jsonObject.addProperty("msisdn", topUpProfile.getMsisdn());
		jsonObject.addProperty("status", status.name());
		jsonObject.addProperty("message", message);
		
		try {
			client = ClientBuilder.newClient();
			response = client.target(apiUserDetails.getNotificationIp()).path(apiUserDetails.getNotificationEndpoint())
			.request(MediaType.APPLICATION_JSON)
			.header("Authorization", "Bearer " + securityToken)
			.post(Entity.entity(new Gson().toJson(jsonObject), MediaType.APPLICATION_JSON), Response.class);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			log.error("", e);
		} finally {
			if (client != null) client.close();
		}
		
		logAPITransaction(response, topupHistory, topUpProfile, subscriber);
	}

	/**
	 * Log notification request parameters.
	 * 
	 * @param response HTTP response
	 * @param topupHistory transaction record
	 * @param topUpProfile end user auto top-up profile
	 * @param subscriber re-seller auto top-up profile
	 */
	private void logAPITransaction(Response response, TopupHistory topupHistory, TopUpProfile topUpProfile,
			Subscriber subscriber) {
		// TODO Auto-generated method stub
		
		ApiTxnLogs apiTxnLogs = new ApiTxnLogs();
		apiTxnLogs.setAmount(topupHistory.getAmount());
		apiTxnLogs.setInvocationTime(Timestamp.valueOf(LocalDateTime.now()));
		apiTxnLogs.setSubscriber(subscriber);
		apiTxnLogs.setReference(topupHistory.getReferenceNo());
		apiTxnLogs.setTopupHistory(topupHistory);
		apiTxnLogs.setTopUpProfile(topUpProfile);
		apiTxnLogs.setApiTxnTypes(ApiTxnTypes.VEND_NOTIFICATION);
		
		if (response != null)
			apiTxnLogs.setHttpStatusCode(response.getStatus());
		
		try {
			queryService.create(apiTxnLogs);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			log.error("", e);
		}
	}

}