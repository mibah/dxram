package de.hhu.bsinfo.dxram.lock;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import de.hhu.bsinfo.dxram.data.ChunkID;
import de.hhu.bsinfo.dxram.event.EventComponent;
import de.hhu.bsinfo.dxram.event.EventListener;
import de.hhu.bsinfo.dxram.logger.LoggerComponent;
import de.hhu.bsinfo.dxram.lookup.event.NodeFailureEvent;
import de.hhu.bsinfo.dxram.util.NodeID;
import de.hhu.bsinfo.dxram.util.NodeRole;
import de.hhu.bsinfo.utils.locks.SpinLock;

/**
 * Implementation of the lock component interface. This provides a peer side locking i.e.
 * the peer owning the chunk stores any information about its locking state.
 * @author Stefan Nothaas <stefan.nothaas@hhu.de> 26.01.16
 */
public class PeerLockComponent extends LockComponent implements EventListener<NodeFailureEvent> {

	private Map<Long, LockEntry> m_lockedChunks = null;
	private Lock m_mapEntryCreationLock = null;
	
	private LoggerComponent m_logger = null;
	private EventComponent m_event = null;
	
	/**
	 * Constructor
	 * @param p_priorityInit Priority for initialization of this component. 
	 * 			When choosing the order, consider component dependencies here.
	 * @param p_priorityShutdown Priority for shutting down this component. 
	 * 			When choosing the order, consider component dependencies here.
	 */
	public PeerLockComponent(int p_priorityInit, int p_priorityShutdown) {
		super(p_priorityInit, p_priorityShutdown);
	}

	@Override
	protected void registerDefaultSettingsComponent(Settings p_settings) {
	}

	@Override
	protected boolean initComponent(de.hhu.bsinfo.dxram.engine.DXRAMEngine.Settings p_engineSettings,
			Settings p_settings) {
		m_logger = getDependentComponent(LoggerComponent.class);
		m_event = getDependentComponent(EventComponent.class);
		
		m_event.registerListener(this, NodeFailureEvent.class);
		
		m_lockedChunks = new HashMap<Long, LockEntry>();
		m_mapEntryCreationLock = new SpinLock();
		
		return true;
	}

	@Override
	protected boolean shutdownComponent() {
		m_logger = null;
		
		m_lockedChunks.clear();
		m_lockedChunks = null;
		m_mapEntryCreationLock = null;

		return true;
	}

	@Override
	public boolean lock(final long p_localID, final short p_lockingNodeID, boolean p_writeLock, int p_timeoutMs) 
	{
		boolean success = false;
		
		// sanity masking of localID
		LockEntry lockEntry = m_lockedChunks.get(ChunkID.getLocalID(p_localID));
		if (lockEntry == null) {
			// create on demand
			lockEntry = new LockEntry();
			
			m_mapEntryCreationLock.lock();
			LockEntry prev = m_lockedChunks.get(ChunkID.getLocalID(p_localID));
			// avoid race condition and use recently created lock if there is one
			if (prev != null) {
				lockEntry = prev;
			} else {
				m_lockedChunks.put(ChunkID.getLocalID(p_localID), lockEntry);
			}
			m_mapEntryCreationLock.unlock();
		}
		
		try {
			
			if (p_timeoutMs == MS_TIMEOUT_UNLIMITED) {
				// unlimited timeout, lock
				lockEntry.m_lock.lock();
				lockEntry.m_nodeID = p_lockingNodeID;
				success = true;
			} else {
				if (lockEntry.m_lock.tryLock(p_timeoutMs, TimeUnit.MILLISECONDS)) {
					lockEntry.m_nodeID = p_lockingNodeID;
					success = true;
				}
			}
		} catch (InterruptedException e) {
			// ignore interrupt exception -> success false
			e.printStackTrace();
		}
		
		return success;
	}

	@Override
	public boolean unlock(final long p_localID, final short p_unlockingNodeID, boolean p_writeLock) {
		
		// sanity masking of localID
		LockEntry lockEntry = m_lockedChunks.get(ChunkID.getLocalID(p_localID));
		if (lockEntry == null) {
			// trying to unlock non locked chunk
			m_logger.error(getClass(), "Unlocking previously non locked chunk " + Long.toHexString(p_localID) + 
										" by node " + Integer.toHexString(p_unlockingNodeID & 0xFFFF) + " not possible.");
			return false;
		}
		
		if (lockEntry.m_nodeID != p_unlockingNodeID) {
			// trying to unlock a chunk we have not locked
			m_logger.error(getClass(), "Unlocking chunk " + Long.toHexString(p_localID) + 
									" locked by node " + Integer.toHexString(lockEntry.m_nodeID & 0xFFFF) + 
									" not allowed for node " + Integer.toHexString(p_unlockingNodeID & 0xFFFF) + ".");
			return false;
		}
		
		// TODO locks are not cleaned up after usage and it's not possible to
		// do this without further locking of the map involved (concurrent get not possible anymore)
		lockEntry.m_nodeID = NodeID.INVALID_ID;
		lockEntry.m_lock.unlock();
		return true;
	}
	
	@Override
	public boolean unlockAllByNodeID(final short p_nodeID)
	{
		// because the node crashed, we can assume that no further locks by this node are added
		for (Entry<Long, LockEntry> entry : m_lockedChunks.entrySet())
		{
			LockEntry lockEntry = entry.getValue();
			if (lockEntry.m_nodeID == p_nodeID) {
				// force unlock
				// TODO lock cleanup? refer to unlock function
				lockEntry.m_nodeID = NodeID.INVALID_ID;
				lockEntry.m_lock.unlock();
			}
		}
		
		return true;
	}
	
	@Override
	public void eventTriggered(NodeFailureEvent p_event) {
		if (p_event.getRole() == NodeRole.PEER) {
			m_logger.debug(getClass(), "Connection to peer " + p_event.getNodeID() + " lost, unlocking all chunks locked by lost instance.");
			
			if (!unlockAllByNodeID(p_event.getNodeID())) {
				m_logger.error(getClass(), "Unlocking all locked chunks of crashed peer " + 
											p_event.getNodeID() + " failed.");
			}	
		}
	}

	/**
	 * Entry for the lock map.
	 * @author Stefan Nothaas <stefan.nothaas@hhu.de> 26.01.16
	 */
	private static class LockEntry
	{
		/**
		 * Lock for the chunk.
		 */
		public Lock m_lock = new SpinLock();
		/**
		 * ID of the node that has locked the chunk.
		 */
		public short m_nodeID = NodeID.INVALID_ID;
	}
}
