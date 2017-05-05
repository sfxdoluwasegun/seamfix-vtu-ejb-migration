/**
 * 
 */
package com.sf.vas.mtnvtu.enums;

/**
 * @author dawuzi
 *
 */
public enum VtuMtnSetting {

    VTU_ORIGINATOR_MSISDN("2348064616904", "Airtime Pool Msisdn"),
    VTU_USE_EMBEDDED_WSDL_FILE("FALSE", "Indicates wether to use the embedded wsdl file"),
    VTU_WSDL_FILE_URL("http://41.206.4.75:8083/axis2/services/HostIFService?wsdl", "URL to the wsdl file. Used when the VTU_USE_EMBEDDED_WSDL_FILE setting is not true"),
    VTU_SERVICE_PROVIDER_ID("1", "VTU service provided id"),
    VTU_MINIMUM_TRANSFER_AMOUNT("50", "Minimum amount that can be transfered"),
    VTU_CURRENT_SEQUENCE_NUMBER("1", "Current VTU sequence number"),
    VTU_VEND_SERVICE_URL("1", "url for the vend operation"),
    VTU_VEND_USERNAME("", "vend username"),
    VTU_VEND_PASSWORD("", "vend password"),
    
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
