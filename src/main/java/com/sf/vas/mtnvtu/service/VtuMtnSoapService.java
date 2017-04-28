/**
 * 
 */
package com.sf.vas.mtnvtu.service;

import java.net.MalformedURLException;
import java.net.URL;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.inject.Inject;

import org.jboss.logging.Logger;

import com.sf.vas.mtnvtu.enums.VtuMtnSetting;
import com.sf.vas.mtnvtu.soapartifacts.HostIFService;
import com.sf.vas.mtnvtu.soapartifacts.HostIFServicePortType;
import com.sf.vas.mtnvtu.tools.VtuMtnQueryService;
import com.sf.vas.utils.exception.VasRuntimeException;

/**
 * It was made a Singleton cos of the sequential constraint on the MTN VTU service
 * @author dawuzi
 *
 */
@Singleton
public class VtuMtnSoapService {

	private Logger log = Logger.getLogger(getClass());
	
	@Inject
	VtuMtnQueryService vtuQueryService;
	
	private HostIFService hostIFService;
	
	private HostIFServicePortType hostIFServicePortType;
	
	@PostConstruct
	private void init() {
		
		String useWsdlFile = vtuQueryService.getSettingValue(VtuMtnSetting.VTU_USE_EMBEDDED_WSDL_FILE);
		
		log.info(getClass().getName() + " init called, useWsdlFile : "+useWsdlFile);
		
		if(useWsdlFile != null && useWsdlFile.trim().equalsIgnoreCase("true")){
			hostIFService = new HostIFService(HostIFService.class.getResource("HostIFService.wsdl"));
		} else {

			String wsdlUrl;
			
			wsdlUrl = vtuQueryService.getSettingValue(VtuMtnSetting.VTU_WSDL_FILE_URL);
			
			log.info("wsdlUrl : "+wsdlUrl);
			
			if(wsdlUrl != null){
				try {
					hostIFService = new HostIFService(new URL(wsdlUrl));
				} catch (MalformedURLException e) {
					throw new VasRuntimeException("Invalid setting WSDL URL", e);
				}
				
			} else {
				hostIFService = new HostIFService(HostIFService.class.getResource("HostIFService.wsdl"));
			}
		}
		
		hostIFServicePortType = hostIFService.getHostIFServiceSOAP11PortHttp();
	}

	/**
	 * @return the hostIFService
	 */
	public HostIFServicePortType getHostIFServicePortType() {
		return hostIFServicePortType;
	}
	
}
