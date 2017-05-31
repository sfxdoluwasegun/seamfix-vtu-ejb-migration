/**
 * 
 */
package com.sf.vas.mtnvtu.enums;

/**
 * @author dawuzi
 *
 */
public enum SmsProps {

    CREDIT_SUCCESSFUL("successful-credit-message", 
    		"Y'ello, you have been successfully credited with NGN {amount} from your AutoTopUp account. Thanks for choosing AutoTopUp", 
    		"parameters include amount"),
	;
	
	SmsProps(String key, String defaultValue, String defaultDescription) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.defaultDescription = defaultDescription;
    }

    private String key;
    private String defaultValue;
    private String defaultDescription;
    
	/**
	 * @return the key
	 */
	public String getKey() {
		return key;
	}
	/**
	 * @return the defaultValue
	 */
	public String getDefaultValue() {
		return defaultValue;
	}
	/**
	 * @return the defaultDescription
	 */
	public String getDefaultDescription() {
		return defaultDescription;
	}
    
    
}
