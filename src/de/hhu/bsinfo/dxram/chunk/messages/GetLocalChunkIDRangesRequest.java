
package de.hhu.bsinfo.dxram.chunk.messages;

import de.hhu.bsinfo.menet.AbstractRequest;

/**
 * Request for getting the chunk id ranges of locally stored chunk ids from another node.
 * @author Stefan Nothaas <stefan.nothaas@hhu.de> 03.02.16
 */
public class GetLocalChunkIDRangesRequest extends AbstractRequest {
	/**
	 * Creates an instance of GetLocalChunkIDRangesRequest.
	 * This constructor is used when receiving this message.
	 */
	public GetLocalChunkIDRangesRequest() {
		super();
	}

	/**
	 * Creates an instance of GetLocalChunkIDRangesRequest.
	 * This constructor is used when sending this message.
	 * @param p_destination
	 *            the destination node id.
	 */
	public GetLocalChunkIDRangesRequest(final short p_destination) {
		super(p_destination, ChunkMessages.TYPE, ChunkMessages.SUBTYPE_GET_LOCAL_CHUNKID_RANGES_REQUEST);
	}
}
