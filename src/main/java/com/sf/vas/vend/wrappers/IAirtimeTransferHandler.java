package com.sf.vas.vend.wrappers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sf.vas.utils.restartifacts.vtu.AirtimeTransferResponse;
import com.sf.vas.vend.dto.AirtimeTransferRequestDTO;

/**
 * @author DAWUZI
 *
 */

public abstract class IAirtimeTransferHandler {
	
	protected Logger log = LoggerFactory.getLogger(getClass());
	
	public abstract AirtimeTransferResponse handleTransferAirtime(AirtimeTransferRequestDTO request);
	
}
