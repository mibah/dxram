package de.uniduesseldorf.dxram.core.lock.messages;

import java.nio.ByteBuffer;

import de.uniduesseldorf.dxram.core.data.DataStructure;
import de.uniduesseldorf.dxram.core.util.ChunkID;
import de.uniduesseldorf.dxram.core.util.ChunkMessagesMetadataUtils;

import de.uniduesseldorf.menet.AbstractMessage;

/**
 * Request for unlocking Chunks on a remote node
 * @author Florian Klein 09.03.2012
 * @author Stefan Nothaas <stefan.nothaas@hhu.de> 5.1.16
 */
public class UnlockMessage extends AbstractMessage {

	// Attributes
	private long m_chunkID = ChunkID.INVALID_ID;

	// Constructors
	/**
	 * Creates an instance of UnlockRequest as a receiver.
	 */
	public UnlockMessage() {
		super();
	}

	/**
	 * Creates an instance of UnlockRequest as a sender
	 * @param p_destination
	 *            the destination node ID.
	 * @param p_writeLock
	 *            True for the write lock, false for read lock.
	 * @param p_dataStructure
	 * 			  Data structure to be unlocked.
	 */
	public UnlockMessage(final short p_destination, final boolean p_writeLock, final DataStructure p_dataStructure) {
		super(p_destination, LockMessages.TYPE, LockMessages.SUBTYPE_UNLOCK_MESSAGE);

		m_chunkID = p_dataStructure.getID();
		
		if (p_writeLock) {
			setStatusCode(ChunkMessagesMetadataUtils.setWriteLockFlag(getStatusCode(), true));
		} else {
			setStatusCode(ChunkMessagesMetadataUtils.setReadLockFlag(getStatusCode(), true));
		}
	}

	/**
	 * Get the chunk ID to unlock (when receiving).
	 * @return Chunk ID to unlock.
	 */
	public long getChunkID() {
		return m_chunkID;
	}
	
	/**
	 * Get the lock operation to execute (when receiving).
	 * @return True for write lock, false read lock.
	 */
	public boolean isWriteLockOperation() {
		if (ChunkMessagesMetadataUtils.isLockAcquireFlagSet(getStatusCode())) {
			if (ChunkMessagesMetadataUtils.isReadLockFlagSet(getStatusCode())) {
				return false;
			} else {
				return true;
			}
		} else {
			assert 1 == 2;
			return true;
		}
	}

	// Methods
	@Override
	protected final void writePayload(final ByteBuffer p_buffer) {
		p_buffer.putLong(m_chunkID);
	}

	@Override
	protected final void readPayload(final ByteBuffer p_buffer) {
		m_chunkID = p_buffer.getLong();
	}

	@Override
	protected final int getPayloadLengthForWrite() {
		return Long.BYTES;
	}

}
