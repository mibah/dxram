package de.hhu.bsinfo.dxram.migration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import de.hhu.bsinfo.dxram.backup.BackupComponent;
import de.hhu.bsinfo.dxram.boot.BootComponent;
import de.hhu.bsinfo.dxram.chunk.ChunkComponent;
import de.hhu.bsinfo.dxram.chunk.messages.ChunkMessages;
import de.hhu.bsinfo.dxram.data.Chunk;
import de.hhu.bsinfo.dxram.engine.DXRAMService;
import de.hhu.bsinfo.dxram.log.messages.RemoveMessage;
import de.hhu.bsinfo.dxram.lookup.LookupComponent;
import de.hhu.bsinfo.dxram.mem.MemoryManagerComponent;
import de.hhu.bsinfo.dxram.migration.messages.MigrationMessage;
import de.hhu.bsinfo.dxram.migration.messages.MigrationMessages;
import de.hhu.bsinfo.dxram.net.NetworkComponent;
import de.hhu.bsinfo.dxram.util.NodeRole;
import de.hhu.bsinfo.menet.AbstractMessage;
import de.hhu.bsinfo.menet.NetworkHandler.MessageReceiver;

/**
 * Migration service providing migration of chunks.
 * @author Kevin Beineke <kevin.beineke@hhu.de> 30.03.16
 */
public class MigrationService extends DXRAMService implements MessageReceiver {

	private BootComponent m_boot = null;
	private BackupComponent m_backup = null;
	private ChunkComponent m_chunk = null;
	private LookupComponent m_lookup = null;
	private MemoryManagerComponent m_memoryManager = null;
	private NetworkComponent m_network = null;
	
	private Lock m_migrationLock;
	
	@Override
	protected void registerDefaultSettingsService(Settings p_settings) {
	}

	@Override
	protected boolean startService(de.hhu.bsinfo.dxram.engine.DXRAMEngine.Settings p_engineSettings,
			Settings p_settings) {
		m_boot = getComponent(BootComponent.class);
		m_backup = getComponent(BackupComponent.class);
		m_chunk = getComponent(ChunkComponent.class);
		m_lookup = getComponent(LookupComponent.class);
		m_memoryManager = getComponent(MemoryManagerComponent.class);
		m_network = getComponent(NetworkComponent.class);
		
		m_migrationLock = new ReentrantLock(false);
		
		registerNetworkMessageListener();
		
		return true;
	}

	@Override
	protected boolean shutdownService() {
		return true;
	}
	
	
	/**
	 * Migrates the corresponding Chunk for the giving ID to another Node
	 * @param p_chunkID
	 *            the ID
	 * @param p_target
	 *            the Node where to migrate the Chunk
	 * @return true=success, false=failed
	 */
	public boolean migrate(final long p_chunkID, final short p_target) {
		short[] backupPeers;
		Chunk chunk;
		boolean ret = false;

		if (m_boot.getNodeRole().equals(NodeRole.SUPERPEER)) {
			// LOGGER.error("a superpeer must not store chunks");
		} else {
			m_migrationLock.lock();
			if (p_target != m_boot.getNodeID() && m_memoryManager.exists(p_chunkID)) {
				int size;

				chunk = null;

				m_memoryManager.lockAccess();
				size = m_memoryManager.getSize(p_chunkID);
				chunk = new Chunk(p_chunkID, size);
				m_memoryManager.get(chunk);
				m_memoryManager.unlockAccess();

				if (chunk != null) {
					// LOGGER.trace("Send request to " + p_target);
					m_network.sendMessage(new MigrationMessage(p_target, new Chunk[] {chunk}));

					// Update superpeers
					m_lookup.migrate(p_chunkID, p_target);
					
					// TODO:
					// Remove all locks
					//m_lock.unlockAll(p_chunkID);
					
					// Update local memory management
					m_memoryManager.remove(p_chunkID);
					if (m_backup.isActive()) {
						// Update logging
						backupPeers = m_backup.getBackupPeersForLocalChunks(p_chunkID);
						if (backupPeers != null) {
							for (int i = 0; i < backupPeers.length; i++) {
								if (backupPeers[i] != m_boot.getNodeID() && backupPeers[i] != -1) {
									m_network.sendMessage(new RemoveMessage(backupPeers[i], new Long[] {p_chunkID}));
								}
							}
						}
					}
					ret = true;
				}
			} else {
				System.out.println("Chunk with ChunkID " + p_chunkID + " could not be migrated!");
				ret = false;
			}
			m_migrationLock.unlock();
		}
		return ret;
	}

	/**
	 * Migrates the corresponding Chunks for the giving ID range to another Node
	 * @param p_startChunkID
	 *            the first ID
	 * @param p_endChunkID
	 *            the last ID
	 * @param p_target
	 *            the Node where to migrate the Chunks
	 * @return true=success, false=failed
	 */
	public boolean migrateRange(final long p_startChunkID, final long p_endChunkID, final short p_target) {
		long[] chunkIDs = null;
		short[] backupPeers;
		int counter = 0;
		long iter;
		long size;
		Chunk chunk;
		Chunk[] chunks;
		boolean ret = false;

		// TODO: Handle range properly

		if (m_boot.getNodeRole().equals(NodeRole.SUPERPEER)) {
			// LOGGER.error("a superpeer must not store chunks");
		} else {
			if (p_startChunkID <= p_endChunkID) {
				chunkIDs = new long[(int) (p_endChunkID - p_startChunkID + 1)];
				m_migrationLock.lock();
				if (p_target != m_boot.getNodeID()) {
					iter = p_startChunkID;
					while (true) {
						// Send chunks to p_target
						chunks = new Chunk[(int) (p_endChunkID - iter + 1)];
						counter = 0;
						size = 0;
						m_memoryManager.lockAccess();
						while (iter <= p_endChunkID) {
							if (m_memoryManager.exists(iter)) {
								int sizeChunk;

								chunk = null;

								sizeChunk = m_memoryManager.getSize(iter);
								chunk = new Chunk(iter, sizeChunk);
								m_memoryManager.get(chunk);

								chunks[counter] = chunk;
								chunkIDs[counter] = chunk.getID();
								size += chunk.getDataSize();
							} else {
								System.out.println("Chunk with ChunkID " + iter + " could not be migrated!");
							}
							iter++;
						}
						m_memoryManager.unlockAccess();

						System.out.println("Sending " + counter + " Chunks (" + size + " Bytes) to " + p_target);
						m_network.sendMessage(new MigrationMessage(p_target, Arrays.copyOf(chunks, counter)));

						if (iter > p_endChunkID) {
							break;
						}
					}

					// Update superpeers
					m_lookup.migrateRange(p_startChunkID, p_endChunkID, p_target);

					if (m_backup.isActive()) {
						// Update logging
						backupPeers = m_backup.getBackupPeersForLocalChunks(iter);
						if (backupPeers != null) {
							for (int i = 0; i < backupPeers.length; i++) {
								if (backupPeers[i] != m_boot.getNodeID() && backupPeers[i] != -1) {
									m_network.sendMessage(new RemoveMessage(backupPeers[i], chunkIDs));
								}
							}
						}
					}

					iter = p_startChunkID;
					while (iter <= p_endChunkID) {
						// TODO:
						// Remove all locks
						//m_lock.unlockAll(iter);
						
						// Update local memory management
						m_memoryManager.remove(iter);
						iter++;
					}
					ret = true;
				} else {
					System.out.println("Chunks could not be migrated because end of range is before start of range!");
					ret = false;
				}
			} else {
				System.out.println("Chunks could not be migrated!");
				ret = false;
			}
			m_migrationLock.unlock();
			System.out.println("All chunks migrated!");
		}
		return ret;
	}
	
	/**
	 * Migrates all chunks to another node. Is called for promotion.
	 * @param p_target
	 *            the peer that should take over all chunks
	 */
	public void migrateAll(final short p_target) {
		long localID;
		long chunkID;
		Iterator<Long> iter;

		// Migrate all chunks created on this node
		ArrayList<Long> ownChunkRanges = m_memoryManager.getCIDRangesOfAllLocalChunks();
		for (int i = 0; i < ownChunkRanges.size(); i += 2) {
			long firstID = ownChunkRanges.get(i);
			long lastID = ownChunkRanges.get(i + 1);
			for (localID = firstID; localID < lastID; i++) {
				chunkID = ((long) m_boot.getNodeID() << 48) + localID;
				if (m_memoryManager.exists(chunkID)) {
					migrate(chunkID, p_target);
				}
			}	
		}

		// Migrate all chunks migrated to this node
		iter = null;
		iter = m_memoryManager.getCIDOfAllMigratedChunks().iterator();
		while (iter.hasNext()) {
			chunkID = iter.next();
			migrate(chunkID, p_target);
		}
	}	
	
	/**
	 * Handles an incoming MigrationMessage
	 * @param p_message
	 *            the MigrationMessage
	 */
	private void incomingMigrationMessage(final MigrationMessage p_message) {
			m_chunk.putForeignChunks((Chunk[]) p_message.getDataStructures());
	}
	
	@Override
	public void onIncomingMessage(final AbstractMessage p_message) {
		
		if (p_message != null) {
			if (p_message.getType() == ChunkMessages.TYPE) {
				switch (p_message.getSubtype()) {
				case MigrationMessages.SUBTYPE_MIGRATION_MESSAGE:
					incomingMigrationMessage((MigrationMessage) p_message);
					break;
				default:
					break;
				}
			}
		}
	}
	
	// -----------------------------------------------------------------------------------

	/**
	 * Register network messages we want to listen to in here.
	 */
	private void registerNetworkMessageListener()
	{
		m_network.register(MigrationMessage.class, this);
	}

	
}
