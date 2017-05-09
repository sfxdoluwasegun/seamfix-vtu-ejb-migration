/**
 * 
 */
package com.sf.vas.mtnvtu.service;

import java.util.Map;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.inject.Inject;
import javax.xml.ws.BindingProvider;

import org.jboss.logging.Logger;

import com.sf.vas.mtnvtu.enums.VtuMtnSetting;
import com.sf.vas.mtnvtu.soapartifacts.HostIFService;
import com.sf.vas.mtnvtu.soapartifacts.HostIFServicePortType;
import com.sf.vas.mtnvtu.tools.VtuMtnQueryService;
import com.sf.vas.utils.crypto.EncryptionUtil;
import com.sf.vas.utils.exception.VasException;
import com.sf.vas.utils.exception.VasRuntimeException;

/**
 * It was made a Singleton cos of the sequential constraint on the MTN VTU service
 * @author dawuzi
 *
 */
@Singleton
public class VtuMtnSoapService {

	@SuppressWarnings("unused")
	private Logger log = Logger.getLogger(getClass());
	
	@Inject
	private VtuMtnQueryService vtuQueryService;
	
	private HostIFService hostIFService;
	
	private HostIFServicePortType hostIFServicePortType;
	
	@Inject
	private EncryptionUtil encryptionUtil;
	
	@PostConstruct
	private void init() {

		hostIFService = new HostIFService(getClass().getClassLoader().getResource("soapresources/HostIFService.wsdl"));
		
		hostIFServicePortType = hostIFService.getHostIFServiceSOAP11PortHttp();
		
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
		
		BindingProvider bindingProvider = (BindingProvider) hostIFServicePortType;
		
		Map<String, Object> requestContext = bindingProvider.getRequestContext();
		
		requestContext.put(BindingProvider.USERNAME_PROPERTY, username);
		requestContext.put(BindingProvider.PASSWORD_PROPERTY, password);
		requestContext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpointUrl);
	
	}

	/**
	 * @return the hostIFService
	 */
	public HostIFServicePortType getHostIFServicePortType() {
		return hostIFServicePortType;
	}
	
}
