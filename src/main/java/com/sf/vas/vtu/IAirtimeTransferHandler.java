package com.sf.vas.vtu;

import com.sf.vas.mtnvtu.dto.AirtimeTransferRequestDTO;
import com.sf.vas.utils.restartifacts.vtu.AirtimeTransferResponse;

/**
 * @author DAWUZI
 *
 */

public interface IAirtimeTransferHandler {
	
	AirtimeTransferResponse handleTransferAirtime(AirtimeTransferRequestDTO request);
	
}
