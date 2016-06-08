
package de.hhu.bsinfo.dxram.log.storage;

import java.util.Arrays;

import de.hhu.bsinfo.dxram.logger.LoggerComponent;

/**
 * HashTable to store versions (Linear probing)
 * @author Kevin Beineke
 *         28.11.2014
 */
public class VersionsHashTable {

	// Attributes
	private LoggerComponent m_logger;

	private int[] m_table;
	private int m_count;
	private int m_elementCapacity;

	// Constructors
	/**
	 * Creates an instance of VersionsHashTable
	 * @param p_initialElementCapacity
	 *            the initial capacity of VersionsHashTable
	 * @param p_logger
	 *            the logger component
	 */
	public VersionsHashTable(final int p_initialElementCapacity, final LoggerComponent p_logger) {
		super();

		m_logger = p_logger;

		m_count = 0;
		m_elementCapacity = p_initialElementCapacity;
		if (p_initialElementCapacity == 0) {
			m_elementCapacity = 100;
		}

		if (m_elementCapacity == 0) {
			m_table = new int[4];
		} else {
			m_table = new int[m_elementCapacity * 4];
		}
	}

	// Getter
	/**
	 * Returns the number of keys in VersionsHashTable
	 * @return the number of keys in VersionsHashTable
	 */
	protected final int size() {
		return m_count;
	}

	/**
	 * Returns all entries
	 * @return the array
	 */
	protected final int[] getTable() {
		return m_table;
	}

	// Methods
	/**
	 * Clears VersionsHashTable
	 */
	public final void clear() {
		Arrays.fill(m_table, 0);
		m_count = 0;
	}

	/**
	 * Returns the value to which the specified key is mapped in VersionsHashTable
	 * @param p_key
	 *            the searched key (is incremented before insertion to avoid 0)
	 * @return the value to which the key is mapped in VersionsHashTable
	 */
	protected final Version get(final long p_key) {
		Version ret = null;
		int index;
		long iter;
		final long key = p_key + 1;

		index = (VersionsBuffer.hash(key) & 0x7FFFFFFF) % m_elementCapacity;

		iter = getKey(index);
		while (iter != 0) {
			if (iter == key) {
				ret = new Version((short) getEpoch(index), getVersion(index));
				break;
			}
			iter = getKey(++index);
		}

		return ret;
	}

	/**
	 * Maps the given key to the given value in VersionsHashTable
	 * @param p_key
	 *            the key (is incremented before insertion to avoid 0)
	 * @param p_epoch
	 *            the epoch
	 * @param p_version
	 *            the version
	 */
	protected void put(final long p_key, final int p_epoch, final int p_version) {
		int index;
		long iter;
		final long key = p_key + 1;

		index = (VersionsBuffer.hash(key) & 0x7FFFFFFF) % m_elementCapacity;

		iter = getKey(index);
		while (iter != 0) {
			if (iter == key) {
				set(index, key, p_epoch, p_version);
				break;
			}
			iter = getKey(++index);
		}
		if (iter == 0) {
			// Key unknown until now
			set(index, key, p_epoch, p_version);
			m_count++;
		}

		// #if LOGGER >= ERROR
		if (m_count == m_elementCapacity) {
			m_logger.error(VersionsHashTable.class, "HashTable is too small. Rehashing prohibited!");
		}
		// #endif /* LOGGER >= ERROR */
	}

	/**
	 * Gets the key at given index
	 * @param p_index
	 *            the index
	 * @return the key
	 */
	private long getKey(final int p_index) {
		int index;

		index = p_index % m_elementCapacity * 4;
		return (long) m_table[index] << 32 | m_table[index + 1] & 0xFFFFFFFFL;
	}

	/**
	 * Gets the epoch at given index
	 * @param p_index
	 *            the index
	 * @return the epoch
	 */
	private int getEpoch(final int p_index) {
		return m_table[p_index % m_elementCapacity * 4 + 2];
	}

	/**
	 * Gets the version at given index
	 * @param p_index
	 *            the index
	 * @return the version
	 */
	private int getVersion(final int p_index) {
		return m_table[p_index % m_elementCapacity * 4 + 3];
	}

	/**
	 * Sets the key-value tuple at given index
	 * @param p_index
	 *            the index
	 * @param p_key
	 *            the key
	 * @param p_epoch
	 *            the epoch
	 * @param p_version
	 *            the version
	 */
	private void set(final int p_index, final long p_key, final int p_epoch, final int p_version) {
		int index;

		index = p_index % m_elementCapacity * 4;
		m_table[index] = (int) (p_key >> 32);
		m_table[index + 1] = (int) p_key;
		m_table[index + 2] = p_epoch;
		m_table[index + 3] = p_version;
	}
}
