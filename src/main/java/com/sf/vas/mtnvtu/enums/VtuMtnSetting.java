/**
 * 
 */
package com.sf.vas.mtnvtu.enums;

/**
 * @author dawuzi
 *
 */
public enum VtuMtnSetting {

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

}
