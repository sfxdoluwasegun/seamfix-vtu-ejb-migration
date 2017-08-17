/**
 * 
 */
package com.sf.vas.vend.dto;

import java.io.Serializable;
import java.math.BigDecimal;

import com.sf.vas.atjpa.entities.NetworkCarrier;
import com.sf.vas.atjpa.entities.Subscriber;
import com.sf.vas.atjpa.entities.TopUpProfile;
import com.sf.vas.atjpa.entities.TopupHistory;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @author dawuzi
 *
 */
@Getter
@Setter
@ToString
public class AirtimeTransferRequestDTO implements Serializable {

	private static final long serialVersionUID = -3674156609057897222L;

	private String msisdn;
	
	private BigDecimal amount;
	
	private Subscriber subscriber;
	
	private NetworkCarrier networkCarrier;
	
	private TopUpProfile topUpProfile;
	
	private String callbackUrl;

	private TopupHistory topupHistory;

}
