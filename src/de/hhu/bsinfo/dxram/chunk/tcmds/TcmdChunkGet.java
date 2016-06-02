
package de.hhu.bsinfo.dxram.chunk.tcmds;

import java.lang.reflect.InvocationTargetException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import de.hhu.bsinfo.dxram.chunk.ChunkService;
import de.hhu.bsinfo.dxram.data.Chunk;
import de.hhu.bsinfo.dxram.data.ChunkID;
import de.hhu.bsinfo.dxram.data.DataStructure;
import de.hhu.bsinfo.dxram.term.AbstractTerminalCommand;
import de.hhu.bsinfo.dxram.term.TerminalColor;
import de.hhu.bsinfo.utils.Pair;
import de.hhu.bsinfo.utils.args.ArgumentList;
import de.hhu.bsinfo.utils.args.ArgumentList.Argument;

/**
 * This class handles the chunkget commando which lists the content of a specified chunk in the terminal
 * @author Michael Birkhoff <michael.birkhoff@hhu.de> 18.04.16
 */

public class TcmdChunkGet extends AbstractTerminalCommand {

	private static final Argument MS_ARG_CID =
			new Argument("cid", null, true, "Full chunk ID of the chunk to get data from");
	private static final Argument MS_ARG_LID =
			new Argument("lid", null, true, "Separate local id part of the chunk to get data from");
	private static final Argument MS_ARG_NID =
			new Argument("nid", null, true, "Separate node id part of the chunk to get data from");
	private static final Argument MS_ARG_OFF =
			new Argument("offset", "0", true, "Offset within the chunk to start getting data from");
	private static final Argument MS_ARG_LEN = new Argument("length", null, true,
			"Number of bytes to get starting at the specified offset (end of chunk will be truncated)");
	private static final Argument MS_ARG_TYPE =
			new Argument("type", "byte", true, "Format to print the data (str, byte, short, int, long)");
	private static final Argument MS_ARG_HEX =
			new Argument("hex", "true", true, "For some representations, print as hex instead of decimal");
	private static final Argument MS_ARG_CLASS =
			new Argument("class", null, true,
					"Fully qualified name of the class to get the chunk data to (must be a DataStructure)");

	@Override
	public String getName() {
		return "chunkget";
	}

	@Override
	public String getDescription() {
		return "Get a chunk specified by either full cid or separted lid + nid from a storage";

	}

	@Override
	public void registerArguments(final ArgumentList p_arguments) {
		p_arguments.setArgument(MS_ARG_CID);
		p_arguments.setArgument(MS_ARG_LID);
		p_arguments.setArgument(MS_ARG_NID);
		p_arguments.setArgument(MS_ARG_OFF);
		p_arguments.setArgument(MS_ARG_LEN);
		p_arguments.setArgument(MS_ARG_TYPE);
		p_arguments.setArgument(MS_ARG_HEX);
		p_arguments.setArgument(MS_ARG_CLASS);
	}

	@Override
	public boolean execute(final ArgumentList p_arguments) {

		Long cid = p_arguments.getArgumentValue(MS_ARG_CID, Long.class);
		Long lid = p_arguments.getArgumentValue(MS_ARG_LID, Long.class);
		Short nid = p_arguments.getArgumentValue(MS_ARG_NID, Short.class);
		Integer off = p_arguments.getArgumentValue(MS_ARG_OFF, Integer.class);
		Integer len = p_arguments.getArgumentValue(MS_ARG_LEN, Integer.class);
		String dataType = p_arguments.getArgumentValue(MS_ARG_TYPE, String.class);
		boolean hex = p_arguments.getArgumentValue(MS_ARG_HEX, Boolean.class);
		String className = p_arguments.getArgumentValue(MS_ARG_CLASS, String.class);

		ChunkService chunkService = getTerminalDelegate().getDXRAMService(ChunkService.class);

		long chunkId = -1;
		// we favor full cid
		if (cid != null) {
			chunkId = cid;
		} else {
			if (lid != null) {
				if (nid == null) {
					getTerminalDelegate().println("error: missing nid for lid", TerminalColor.RED);
					return false;
				}

				// create cid
				chunkId = ChunkID.getChunkID(nid, lid);
			} else {
				getTerminalDelegate().println("No cid or nid/lid specified.", TerminalColor.RED);
				return false;
			}
		}

		if (className != null) {
			Class<?> clazz;
			try {
				clazz = Class.forName(className);
			} catch (final ClassNotFoundException e) {
				getTerminalDelegate().println("Cannot find class with name " + className, TerminalColor.RED);
				return true;
			}

			if (!DataStructure.class.isAssignableFrom(clazz)) {
				getTerminalDelegate()
						.println("Class " + className + " is not implementing the DataStructure interface");
				return true;
			}

			DataStructure dataStructure;
			try {
				dataStructure = (DataStructure) clazz.getConstructor().newInstance();
			} catch (final InstantiationException | IllegalAccessException
					| InvocationTargetException | NoSuchMethodException e) {
				getTerminalDelegate().println("Creating instance of " + className + " failed: " + e.getMessage());
				return false;
			}

			dataStructure.setID(chunkId);

			if (chunkService.get(dataStructure) != 1) {
				getTerminalDelegate().println("Getting data structure " + ChunkID.toHexString(chunkId) + " failed.",
						TerminalColor.RED);
				return true;
			}

			getTerminalDelegate().println("DataStructure " + className + ": ");
			getTerminalDelegate().println(dataStructure);
		} else {
			Pair<Integer, Chunk[]> chunks = chunkService.get(new long[] {chunkId});
			if (chunks.first() == 0) {
				getTerminalDelegate().println("Getting chunk " + ChunkID.toHexString(chunkId) + " failed.",
						TerminalColor.RED);
				return true;
			}
			Chunk chunk = chunks.second()[0];

			// full length if not specified
			if (len == null) {
				len = chunk.getDataSize();
			}

			ByteBuffer buffer = chunk.getData();
			try {
				buffer.position(off);
			} catch (final IllegalArgumentException e) {
				// set to end
				buffer.position(buffer.capacity());
			}

			String str = new String();
			dataType = dataType.toLowerCase();
			if (dataType.equals("str")) {
				byte[] bytes = new byte[buffer.capacity() - buffer.position()];

				try {
					buffer.get(bytes, 0, len);
				} catch (final BufferOverflowException e) {
					// that's fine, trunc data
				}

				str = new String(bytes, StandardCharsets.US_ASCII);
			} else if (dataType.equals("byte")) {
				try {
					for (int i = 0; i < len; i += Byte.BYTES) {
						if (hex) {
							str += Integer.toHexString(buffer.get() & 0xFF) + " ";
						} else {
							str += buffer.get() + " ";
						}
					}
				} catch (final BufferOverflowException e) {
					// that's fine, trunc data
				}
			} else if (dataType.equals("short")) {
				try {
					for (int i = 0; i < len; i += Short.BYTES) {
						if (hex) {
							str += Integer.toHexString(buffer.getShort() & 0xFFFF) + " ";
						} else {
							str += buffer.getShort() + " ";
						}
					}
				} catch (final BufferOverflowException e) {
					// that's fine, trunc data
				}
			} else if (dataType.equals("int")) {
				try {
					for (int i = 0; i < len; i += Integer.BYTES) {
						if (hex) {
							str += Integer.toHexString(buffer.getInt() & 0xFFFFFFFF) + " ";
						} else {
							str += buffer.getInt() + " ";
						}
					}
				} catch (final BufferOverflowException e) {
					// that's fine, trunc data
				}
			} else if (dataType.equals("long")) {
				try {
					for (int i = 0; i < len; i += Long.BYTES) {
						if (hex) {
							str += Long.toHexString(buffer.getLong() & 0xFFFFFFFFFFFFFFFFL) + " ";
						} else {
							str += buffer.getLong() + " ";
						}
					}
				} catch (final BufferOverflowException e) {
					// that's fine, trunc data
				}
			} else {
				getTerminalDelegate().println("error: Unsupported data type " + dataType, TerminalColor.RED);
				return true;
			}

			getTerminalDelegate().println("Chunk data of " + ChunkID.toHexString(chunkId) + ":");
			getTerminalDelegate().println(str);
		}

		return true;
	}
}
