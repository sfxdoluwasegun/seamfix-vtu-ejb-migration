/**
 * 
 */
package com.sf.vas.vend.enums;

import com.sf.vas.atjpa.enums.SettingsType;
import com.sf.vas.dsl.contracts.ISettingInfo;

/**
 * @author dawuzi
 *
 */
public enum VasVendSetting implements ISettingInfo {

    VTU_ORIGINATOR_MSISDN("2348068735763", "Airtime Pool Msisdn"),
    VTU_SERVICE_URL("http://41.206.4.75:8083/axis2/services/HostIFService", "This is the URL to the VTU service"),
    VTU_SERVICE_PROVIDER_ID("1", "VTU service provided id"),
    VTU_MINIMUM_TRANSFER_AMOUNT("50", "Minimum amount that can be transfered"),
    VTU_CURRENT_SEQUENCE_NUMBER("1", "Current VTU sequence number"),
    VTU_VEND_USERNAME("Uve1RBlifQ2cdN6n5VOAHA==", "vend username"),
    VTU_VEND_PASSWORD("yy+c5vgxg1PZesNOTzp6ww==", "vend password"),
//    SSL_TRUST_STORE_FILE_PATH("/opt/autotopup-service/bin/vtm_mtnonline_com.keystore", "javax.net.ssl.trustStore value"),
//    SSL_TRUST_STORE_PASSWORD("jgX5oP9F8u7PMWNNeBhkug==", "javax.net.ssl.trustStorePassword value"), // plain value = "test" with defaultSecurityKey
    VTU_FAILED_MAX_RETRIAL_ATTEMPTS("3", "Maximum retrial attempts for failed vtu transactions"),
    VTU_MAX_VEND_SERVICE_UNREACHEABLE_NOTIFICATION_COUNT("2", "Maximum number of times to notify subscriber about an error connecting to a vend service"),
    
    GLO_NG_SERVICE_URL("http://41.203.65.11:8913/topupservice/service", "This is the URL to the glo topup service"),
    GLO_NG_SERVICE_PASSWORD("dX0fvhZ8Ws7I3CUsFXNXIQ==", "This is the password to the glo topup service"),
    GLO_NG_SENDER_PRINCIPAL_ID("WEB7056670256", "The principal id of the sender reseller. Usually in the format WEB<vending-msisdn> eg WEB7056670256"),    
    GLO_NG_SENDER_PRINCIPAL_USER_ID("9900", "This defines which user made the topup"),
    
    CREDIT_SWITCH_INIT_PATH("https://creditswitch.net/api/v1/", "Credit switch init path"),
    CREDIT_SWITCH_LOGIN_ID("16412", "Credit switch login ID"),
    CREDIT_SWITCH_PRIVATE_KEY("G3BZ5PFLRE2QGOr0xr7UOA==", "Credit switch private key"),
    CREDIT_SWITCH_PUBLIC_KEY("Ly824mfwOmF5d9DTYZtNzg==", "Credit switch public key"),
    
    
    ;

	VasVendSetting(String defaultValue, String defaultDescription) {
        this.defaultValue = defaultValue;
        this.defaultDescription = defaultDescription;
    }

    private String defaultValue;

    private String defaultDescription;

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getDefaultDescription() {
        return defaultDescription;
    }
    
	public String getName() {
		return name();
	}
	
	public SettingsType getSettingsType() {
		return SettingsType.GENERAL;
	}

}
