package de.hhu.bsinfo.dxram.lookup;

import java.util.List;

import de.hhu.bsinfo.dxram.backup.BackupRange;
import de.hhu.bsinfo.dxram.boot.BootComponent;
import de.hhu.bsinfo.dxram.data.ChunkID;
import de.hhu.bsinfo.dxram.engine.DXRAMEngine;
import de.hhu.bsinfo.dxram.logger.LoggerComponent;
import de.hhu.bsinfo.dxram.util.NodeRole;
import de.hhu.bsinfo.utils.Cache;

public class CachedLookupComponent extends LookupComponent {

	private BootComponent m_boot = null;
	private LoggerComponent m_logger = null;
	
	private DefaultLookupComponent m_lookup = new DefaultLookupComponent(-1, -1);
	private Cache<Long, Long> m_chunkIDCache;
	private Cache<Long, Long> m_applicationIDCache;
	
	public CachedLookupComponent(int p_priorityInit, int p_priorityShutdown) {
		super(p_priorityInit, p_priorityShutdown);
	}

	// ------------------------------------------------------------------------------------

	@Override
	public Locations get(final long p_chunkID) {
		return get(p_chunkID, false);
	}

	@Override
	public BackupRange[] getAllBackupRanges(final short p_nodeID) {
		return m_lookup.getAllBackupRanges(p_nodeID);
	}

	/**
	 * Get the corresponding primary peer and backup peers for the given ID
	 * @param p_chunkID
	 *            the ID
	 * @param p_force
	 *            writes a new entry if no entry exists
	 * @return the corresponding primary peer and backup peers
	 * @throws LookupException
	 *             if the NodeID could not be get
	 */
	public Locations get(final long p_chunkID, final boolean p_force) {
		Long locations;
		Locations ret;

		assert p_chunkID != ChunkID.INVALID_ID;
		
		locations = m_chunkIDCache.get(p_chunkID);
		if (p_force || null == locations) {
			m_logger.trace(getClass(), "value not cached: " + p_chunkID);

			ret = m_lookup.get(p_chunkID);

			m_chunkIDCache.put(p_chunkID, ret.convertToLong());
		} else {
			ret = new Locations(locations.longValue());
		}

		return ret;
	}

	@Override
	public void updateAllAfterRecovery(final short p_owner) {
		m_lookup.updateAllAfterRecovery(p_owner);
	}

	@Override
	public void migrate(final long p_chunkID, final short p_nodeID) {
		assert p_chunkID != ChunkID.INVALID_ID;

		invalidate(p_chunkID);

		m_lookup.migrate(p_chunkID, p_nodeID);
	}

	@Override
	public void migrateRange(final long p_startCID, final long p_endCID, final short p_nodeID) {
		assert p_startCID != ChunkID.INVALID_ID;
		assert p_endCID != ChunkID.INVALID_ID;

		invalidate(p_startCID, p_endCID);

		m_lookup.migrateRange(p_startCID, p_endCID, p_nodeID);
	}

	@Override
	public void migrateNotCreatedChunk(final long p_chunkID, final short p_nodeID) {
		assert p_chunkID != ChunkID.INVALID_ID;

		invalidate(p_chunkID);

		m_lookup.migrateNotCreatedChunk(p_chunkID, p_nodeID);
	}

	@Override
	public void migrateOwnChunk(final long p_chunkID, final short p_nodeID) {
		assert p_chunkID != ChunkID.INVALID_ID;

		invalidate(p_chunkID);

		m_lookup.migrateOwnChunk(p_chunkID, p_nodeID);
	}

	@Override
	public void initRange(final long p_firstChunkID, final Locations p_locations) {
		assert p_firstChunkID != ChunkID.INVALID_ID;

		m_lookup.initRange(p_firstChunkID, p_locations);
	}

	@Override
	public void insertID(final int p_id, final long p_chunkID) {
		m_lookup.insertID(p_id, p_chunkID);
	}

	@Override
	public long getChunkID(final int p_id) {
		long ret;
		Long chunkID;

		chunkID = m_applicationIDCache.get((long) p_id);
		if (null == chunkID) {
			m_logger.trace(getClass(), "value not cached: " + p_id);

			ret = m_lookup.getChunkID(p_id);

			m_applicationIDCache.put((long) p_id, ret);
		} else {
			ret = chunkID.longValue();
		}
		return ret;
	}

	@Override
	public long getMappingCount() {
		return m_lookup.getMappingCount();
	}

	@Override
	public void remove(final long p_chunkID) {
		assert p_chunkID != ChunkID.INVALID_ID;

		invalidate(p_chunkID);

		m_lookup.remove(p_chunkID);
	}

	@Override
	public void remove(final long[] p_chunkIDs) {
		invalidate(p_chunkIDs);

		m_lookup.remove(p_chunkIDs);
	}

	@Override
	public void invalidate(final long... p_chunkIDs) {
		for (long chunkID : p_chunkIDs) {
			assert chunkID != ChunkID.INVALID_ID;
			m_chunkIDCache.remove(chunkID);
		}
	}

	@Override
	public void invalidate(final long p_startCID, final long p_endCID) {
		long iter = p_startCID;
		while (iter <= p_endCID) {
			invalidate(iter++);
		}
	}

	@Override
	public boolean creatorAvailable(final short p_creator) {
		return m_lookup.creatorAvailable(p_creator);
	}

	@Override
	public boolean isLastSuperpeer() {
		return m_lookup.isLastSuperpeer();
	}

	@Override
	public List<Short> getSuperpeers() {
		return m_lookup.getSuperpeers();
	}

	@Override
	public boolean overlayIsStable() {
		return m_lookup.overlayIsStable();
	}

	/**
	 * Clear the cache
	 */
	public void clear() {
		m_chunkIDCache.clear();
	}	
	
	// ------------------------------------------------------------------------------------
	
	@Override
	protected void registerDefaultSettingsComponent(Settings p_settings) {
		p_settings.setDefaultValue(LookupConfigurationValues.Component.SLEEP);
		p_settings.setDefaultValue(LookupConfigurationValues.Component.CACHE_ENTRIES);
		p_settings.setDefaultValue(LookupConfigurationValues.Component.CACHE_TTL);
	}
	
	@Override
	protected boolean initComponent(final DXRAMEngine.Settings p_engineSettings, final Settings p_settings) {
		m_boot = getDependentComponent(BootComponent.class);
		
		m_lookup.initComponent(p_engineSettings, p_settings);
		if (!m_boot.getNodeRole().equals(NodeRole.SUPERPEER)) {
			Long cacheTTL = p_settings.getValue(LookupConfigurationValues.Component.CACHE_TTL);
			m_chunkIDCache.enableTTL(cacheTTL);
			m_applicationIDCache.enableTTL(cacheTTL);
		} else {
			m_chunkIDCache = null;
			m_applicationIDCache = null;
		}
		
		return true;
	}

	@Override
	protected boolean shutdownComponent() {
		return m_lookup.shutdownComponent();
	}
}
