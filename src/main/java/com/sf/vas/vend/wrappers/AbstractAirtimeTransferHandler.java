package com.sf.vas.vend.wrappers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sf.vas.utils.restartifacts.vtu.AirtimeTransferResponse;
import com.sf.vas.vend.dto.AirtimeTransferRequestDTO;
import com.sf.vas.vend.util.VendUtil;

/**
 * @author DAWUZI
 *
 */

public abstract class AbstractAirtimeTransferHandler {
	
	protected Logger log = LoggerFactory.getLogger(getClass());
	
	protected VendUtil vendUtil = new VendUtil();
	
	public abstract AirtimeTransferResponse handleTransferAirtime(AirtimeTransferRequestDTO request);
	
}
