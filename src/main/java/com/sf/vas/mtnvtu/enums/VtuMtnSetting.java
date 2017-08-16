/**
 * 
 */
package com.sf.vas.mtnvtu.enums;

import com.sf.vas.atjpa.enums.SettingsType;
import com.sf.vas.dsl.contracts.ISettingInfo;

/**
 * @author dawuzi
 *
 */
public enum VtuMtnSetting implements ISettingInfo {

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
    
    GLO_NG_SERVICE_URL("http://41.203.65.11:8913/topupservice/service", "This is the URL to the glo topup service"),
    GLO_NG_SERVICE_PASSWORD("dX0fvhZ8Ws7I3CUsFXNXIQ==", "This is the password to the glo topup service"),
    GLO_NG_SENDER_PRINCIPAL_ID("WEB80000000001", "The principal id of the sender reseller"),    
    GLO_NG_SENDER_PRINCIPAL_TYPE("RESELLERUSER", "The type of the sender principal ID which is typically RESELLERUSER"),
    GLO_NG_SENDER_PRINCIPAL_USER_ID("9900", "This defines which user made the topup"),
    GLO_NG_SENDER_ACCOUNT_SPECIFIER_ID("WEB7056670256", "This is the account type of sender reseller. Usually in the format WEB<vending-msisdn> eg WEB7056670256"),
    GLO_NG_SENDER_ACCOUNT_SPECIFIER_TYPE_ID("RESELLER", "This the account type id for the sender account specifier"),

    ;

	VtuMtnSetting(String defaultValue, String defaultDescription) {
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
