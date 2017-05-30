/**
 * 
 */
package com.sf.vas.mtnvtu.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.annotation.PostConstruct;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sf.vas.mtnsms.service.SmsMtnService;
import com.sf.vas.mtnvtu.enums.SmsProps;
import com.sf.vas.utils.exception.VasException;
import com.sf.vas.utils.properties.VasProperties;
import com.sf.vas.utils.restartifacts.sms.SmsRequest;

/**
 * @author dawuzi
 *
 */
@Stateless
public class VtuMtnAsyncService {

	private Logger log = LoggerFactory.getLogger(getClass());
	private VasProperties vasProperties = new VasProperties();
	private boolean initialized = false;
	
	@Inject
	SmsMtnService smsMtnService;
	
	public VtuMtnAsyncService() {
	}
	
	@PostConstruct
	private void init(){
		initProperties();
	}
	
	private void initProperties() {
		if(initialized){
			return;
		}
		log.info("init properties called");
		initialized = true;
		try {
			String smsPropsFile = System.getProperty("jboss.home.dir")+File.separator+"bin"+File.separator+"sms.properties";
			log.info("smsPropsFile : "+smsPropsFile);
			File file = new File(smsPropsFile);
			if(!file.exists()){
				log.warn("sms properties file does not exist"); 
				return;
			}
			log.info("loading sms properties");
			vasProperties.initProps(new FileInputStream(file));
		} catch (FileNotFoundException e) {
			log.error("FileNotFoundException", e);
		} catch (IOException e) {
			log.error("IOException", e);
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
		initProperties();
		
		String message = vasProperties.getProperty(smsProps.getKey(), smsProps.getDefaultValue(), param, value);
		
		SmsRequest smsRequest = new SmsRequest();
		
		smsRequest.setMessage(message);
		smsRequest.setMsisdn(msisdn);
		
		log.info("message : "+message);
		
		try {
			smsMtnService.sendSms(smsRequest);
		} catch (VasException e) {
			log.error("VasException", e);
		}
	}
}
