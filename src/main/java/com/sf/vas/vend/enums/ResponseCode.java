/**
 * 
 */
package com.sf.vas.vend.enums;

/**
 * @author dawuzi
 *
 */

import java.util.HashSet;
import java.util.Set;

import com.sf.vas.utils.restartifacts.IResponseCode;

public enum ResponseCode implements IResponseCode {
	
    SUCCESS("00", "Successful"),
    ERROR("01", "Error"), //usually triggered by an exception
    RETRY("02", "Retry"),
    INVALID_REQUEST("03", "Invalid request"),
    UNKNOWN_USER("04", "Unknown user"),
    INVALID_AMOUNT("05", "Invalid transfer amount"),
    UNKNOWN_NETWORK_CARRIER("06", "Unknown network carrier"),
    UNKNOWN_TOP_UP_PROFILE("07", "Unknown top up profile"),
    UNKNOWN_TOP_UP_HISTORY("08", "Unknown top up history"),
    ;
    
	static {
		validateUniqueResponseCodes();
	}

	private static void validateUniqueResponseCodes() {
		Set<String> seenCodes = new HashSet<>();
		for (ResponseCode code : ResponseCode.values()) {
			if(!seenCodes.add(code.getResponseCode())){
				throw new IllegalStateException("duplicate response codes : "+code.getResponseCode());
			}
		}
	}

    private String responseCode;
    private String responseDescription;

    private ResponseCode(String responseCode, String responseDescription) {
        this.responseCode = responseCode;
        this.responseDescription = responseDescription;
    }

	public String getResponseCode() {
        return responseCode;
    }

    public String getResponseDescription() {
        return responseDescription;
    }

}

