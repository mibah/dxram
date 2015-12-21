package de.uniduesseldorf.dxram.core.migrate.messages;

import java.nio.ByteBuffer;

import de.uniduesseldorf.dxram.core.data.Chunk;
import de.uniduesseldorf.dxram.core.data.DataStructure;
import de.uniduesseldorf.dxram.core.data.MessagesDataStructureImExporter;

import de.uniduesseldorf.menet.AbstractRequest;

/**
 * Request for storing a Chunk on a remote node after migration
 * @author Florian Klein 09.03.2012
 * @author Stefan Nothaas <stefan.nothaas@hhu.de> 11.12.15
 */
public class MigrationRequest extends AbstractRequest {

	// data structure is used when request is sent
	// chunks are created if data is received
	private DataStructure[] m_dataStructures = null;

	/**
	 * Creates an instance of DataRequest.
	 * This constructor is used when receiving this message.
	 */
	public MigrationRequest() {
		super();
	}

	/**
	 * Creates an instance of DataRequest.
	 * This constructor is used when sending this message.
	 * @param p_destination
	 *            the destination
	 * @param p_dataStructure
	 *            The data structure to migrate.
	 */
	public MigrationRequest(final short p_destination, final DataStructure p_dataStructure) {
		super(p_destination, MigrationMessages.TYPE, MigrationMessages.SUBTYPE_MIGRATION_REQUEST);

		m_dataStructures = new DataStructure[] {p_dataStructure};
	}

	/**
	 * Creates an instance of DataRequest
	 * @param p_destination
	 *            the destination
	 * @param p_dataStructures
	 *            Multiple data structures to migrate
	 */
	public MigrationRequest(final short p_destination, final DataStructure[] p_dataStructures) {
		super(p_destination, MigrationMessages.TYPE, MigrationMessages.SUBTYPE_MIGRATION_REQUEST);

		m_dataStructures = p_dataStructures;
	}

	/**
	 * Get the DataStructures to store
	 * @return the DataStructures to store
	 */
	public final DataStructure[] getDataStructures() {
		return m_dataStructures;
	}

	@Override
	protected final void writePayload(final ByteBuffer p_buffer) {
		MessagesDataStructureImExporter exporter = new MessagesDataStructureImExporter(p_buffer);
		
		p_buffer.putInt(m_dataStructures.length);
		for (DataStructure dataStructure : m_dataStructures) {
			int size = dataStructure.sizeofObject();
			
			p_buffer.putLong(dataStructure.getID());
			exporter.setPayloadSize(size);
			p_buffer.putInt(size);
			exporter.exportObject(dataStructure);
		}
	}

	@Override
	protected final void readPayload(final ByteBuffer p_buffer) {
		MessagesDataStructureImExporter importer = new MessagesDataStructureImExporter(p_buffer);
		
		m_dataStructures = new Chunk[p_buffer.getInt()];
		
		for (int i = 0; i < m_dataStructures.length; i++) {
			long id = p_buffer.getLong();
			int size = p_buffer.getInt();
			
			importer.setPayloadSize(size);
			m_dataStructures[i] = new Chunk(id, size);
			importer.importObject(m_dataStructures[i]);
		}
	}

	@Override
	protected final int getPayloadLengthForWrite() {
		int length = 0;
		length += Long.BYTES * m_dataStructures.length;
		for (DataStructure dataStructure : m_dataStructures) {
			length += dataStructure.sizeofObject();
		}
		
		return length;
	}

}
