package com.sf.vas.vtu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sf.vas.mtnvtu.dto.AirtimeTransferRequestDTO;
import com.sf.vas.utils.restartifacts.vtu.AirtimeTransferResponse;

/**
 * @author DAWUZI
 *
 */

public abstract class IAirtimeTransferHandler {
	
	protected Logger log = LoggerFactory.getLogger(getClass());
	
	public abstract AirtimeTransferResponse handleTransferAirtime(AirtimeTransferRequestDTO request);
	
}
