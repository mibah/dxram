
package de.hhu.bsinfo.dxram.log.messages;

import de.hhu.bsinfo.menet.AbstractRequest;

/**
 * Get Utilization Request
 * @author Kevin Beineke
 *         12.04.2016
 */
public class GetUtilizationRequest extends AbstractRequest {

	// Constructors
	/**
	 * Creates an instance of GetUtilizationRequest
	 */
	public GetUtilizationRequest() {
		super();
	}

	/**
	 * Creates an instance of GetUtilizationRequest
	 * @param p_destination
	 *            the destination
	 */
	public GetUtilizationRequest(final short p_destination) {
		super(p_destination, LogMessages.TYPE, LogMessages.SUBTYPE_GET_UTILIZATION_REQUEST);
	}

}