/*
 * Copyright (C) 2016 Heinrich-Heine-Universitaet Duesseldorf, Institute of Computer Science, Department Operating Systems
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package de.hhu.bsinfo.dxcompute.bench;

import java.util.ArrayList;
import java.util.List;

import de.hhu.bsinfo.dxcompute.ms.TaskContext;
import de.hhu.bsinfo.dxram.chunk.ChunkService;
import de.hhu.bsinfo.dxram.data.ChunkID;
import de.hhu.bsinfo.utils.unit.StorageUnit;

/**
 * Collection of chunk related utility functions used by tasks
 *
 * @author Stefan Nothaas, stefan.nothaas@hhu.de, 25.01.2017
 */
final class ChunkTaskUtils {
    public static final int PATTERN_LOCAL_ONLY = 0;
    public static final int PATTERN_REMOTE_ONLY_SUCCESSOR = 1;
    public static final int PATTERN_REMOTE_ONLY_RANDOM = 2;
    public static final int PATTERN_REMOTE_LOCAL_MIXED_RANDOM = 3;

    /**
     * Static class
     */
    private ChunkTaskUtils() {

    }

    /**
     * Get a chunk range for the specified test pattern
     *
     * @param p_pattern
     *     Pattern id (refer to static ints)
     * @param p_ctx
     *     Context of the task this is called from
     * @param p_chunkService
     *     Chunk service instance
     * @return Chunk ranges for the specified test pattern
     */
    static ArrayList<Long> getChunkRangesForTestPattern(final int p_pattern, final TaskContext p_ctx, final ChunkService p_chunkService) {
        ArrayList<Long> allChunkRanges;

        switch (p_pattern) {
            case PATTERN_LOCAL_ONLY:
                allChunkRanges = p_chunkService.getAllLocalChunkIDRanges();
                break;

            case PATTERN_REMOTE_ONLY_SUCCESSOR:
                short slaveId = p_ctx.getCtxData().getSlaveId();
                short successorSlaveId = (short) ((slaveId + 1) % p_ctx.getCtxData().getSlaveNodeIds().length);

                allChunkRanges = p_chunkService.getAllLocalChunkIDRanges(p_ctx.getCtxData().getSlaveNodeIds()[successorSlaveId]);

                break;

            case PATTERN_REMOTE_ONLY_RANDOM:
                short ownNodeId = p_ctx.getCtxData().getOwnNodeId();

                allChunkRanges = new ArrayList<>();
                for (int i = 0; i < p_ctx.getCtxData().getSlaveNodeIds().length; i++) {
                    if (p_ctx.getCtxData().getSlaveNodeIds()[i] != ownNodeId) {
                        allChunkRanges.addAll(p_chunkService.getAllLocalChunkIDRanges(p_ctx.getCtxData().getSlaveNodeIds()[i]));
                    }
                }

                break;

            case PATTERN_REMOTE_LOCAL_MIXED_RANDOM:
                allChunkRanges = new ArrayList<>();
                for (int i = 0; i < p_ctx.getCtxData().getSlaveNodeIds().length; i++) {
                    allChunkRanges.addAll(p_chunkService.getAllLocalChunkIDRanges(p_ctx.getCtxData().getSlaveNodeIds()[i]));
                }

                break;

            default:
                System.out.println("Unknown pattern " + p_pattern);
                return null;
        }

        // modify ranges to avoid deleting an index chunk
        for (int i = 0; i < allChunkRanges.size(); i += 2) {
            long rangeStart = allChunkRanges.get(i);
            if (ChunkID.getLocalID(rangeStart) == 0) {
                allChunkRanges.set(i, rangeStart + 1);
            }
        }

        return allChunkRanges;
    }

    /**
     * Distribute a total number of chunks (if possible) equally to multiple threads
     *
     * @param p_chunkCount
     *     Total number of chunks to distribute
     * @param p_threadCount
     *     Number of threads to distribute to
     * @return Array if chunk counts for each thread
     */
    public static long[] distributeChunkCountsToThreads(final long p_chunkCount, final int p_threadCount) {
        long count = p_chunkCount;
        long[] chunkCounts = new long[p_threadCount];

        for (int i = 0; i < chunkCounts.length; i++) {
            chunkCounts[i] = p_chunkCount / p_threadCount;
            count -= chunkCounts[i];
        }

        for (int i = 0; i < count; i++) {
            chunkCounts[i]++;
        }

        return chunkCounts;
    }

    /**
     * Distribute chunk ranges to multiple threads
     *
     * @param p_chunkCountsPerThread
     *     Array with total number of chunk IDs for each thread
     * @param p_ranges
     *     Ranges to distribute
     * @return Array of chunk ID ranges distributed to each thread
     */
    public static ArrayList<Long>[] distributeChunkRangesToThreads(final long[] p_chunkCountsPerThread, final List<Long> p_ranges) {
        ArrayList<Long>[] distRanges = new ArrayList[p_chunkCountsPerThread.length];
        for (int i = 0; i < distRanges.length; i++) {
            distRanges[i] = new ArrayList<>();
        }

        int rangeIdx = 0;
        long rangeStart = p_ranges.get(rangeIdx * 2);
        long rangeEnd = p_ranges.get(rangeIdx * 2 + 1);

        for (int i = 0; i < p_chunkCountsPerThread.length; i++) {
            long chunkCount = p_chunkCountsPerThread[i];

            while (chunkCount > 0) {
                long chunksInRange = ChunkID.getLocalID(rangeEnd) - ChunkID.getLocalID(rangeStart) + 1;
                if (chunksInRange >= chunkCount) {
                    distRanges[i].add(rangeStart);
                    distRanges[i].add(rangeStart + chunkCount - 1);

                    rangeStart += chunkCount;
                    chunkCount = 0;
                } else {
                    // chunksInRange < chunkCount
                    distRanges[i].add(rangeStart);
                    distRanges[i].add(rangeEnd);

                    chunkCount -= chunksInRange;

                    rangeIdx++;
                    if (rangeIdx * 2 < p_ranges.size()) {
                        rangeStart = p_ranges.get(rangeIdx * 2);
                        rangeEnd = p_ranges.get(rangeIdx * 2 + 1);
                    } else {
                        break;
                    }
                }
            }
        }

        return distRanges;
    }

    /**
     * Get a random node id except the current node's own one
     *
     * @param p_slaveNodeIds
     *     List of node IDs to select a random id from
     * @param p_ownNodeId
     *     Own node id
     * @return Random node id
     */
    static short getRandomNodeIdExceptOwn(final short[] p_slaveNodeIds, final short p_ownNodeId) {
        short nodeId = p_ownNodeId;

        while (nodeId == p_ownNodeId) {
            nodeId = getRandomNodeId(p_slaveNodeIds);
        }

        return nodeId;
    }

    /**
     * Get a random node id from an array of node ids
     *
     * @param p_slaveNodeIds
     *     Array of node ids to pick from
     * @return Random node id
     */
    static short getRandomNodeId(final short[] p_slaveNodeIds) {
        return p_slaveNodeIds[getRandomRangeExclEnd(0, p_slaveNodeIds.length)];
    }

    /**
     * Get the successor of a node from an array of node ids
     * The successor is simply the node id in the array following the specified id
     *
     * @param p_slaveNodeIds
     *     Array of node ids
     * @param p_ownSlaveId
     *     Own node id
     * @return Successor to own node id from array
     */
    static short getSuccessorSlaveNodeId(final short[] p_slaveNodeIds, final short p_ownSlaveId) {
        if (p_ownSlaveId + 1 < p_slaveNodeIds.length) {
            return p_slaveNodeIds[p_ownSlaveId + 1];
        } else {
            return p_slaveNodeIds[0];
        }
    }

    /**
     * Get a random size from a size range
     *
     * @param p_start
     *     Start of size range (including)
     * @param p_end
     *     End of size range (including)
     * @return Random size in bytes
     */
    static int getRandomSize(final StorageUnit p_start, final StorageUnit p_end) {
        return (int) getRandomRange(p_start.getBytes(), p_end.getBytes());
    }

    /**
     * Get a random number from a specified range
     *
     * @param p_start
     *     Start (including)
     * @param p_end
     *     End (including)
     * @return Random int
     */
    private static int getRandomRange(final int p_start, final int p_end) {
        return (int) (Math.random() * (p_end - p_start + 1) + p_start);
    }

    /**
     * Get a random number from a specified range
     *
     * @param p_start
     *     Start (including)
     * @param p_end
     *     End (excluding)
     * @return Random int
     */
    private static int getRandomRangeExclEnd(final int p_start, final int p_end) {
        return (int) (Math.random() * (p_end - p_start) + p_start);
    }

    /**
     * Get a random number from a specified range
     *
     * @param p_start
     *     Start (including)
     * @param p_end
     *     End (including)
     * @return Random int
     */
    private static long getRandomRange(final long p_start, final long p_end) {
        return (long) (Math.random() * (p_end - p_start + 1) + p_start);
    }
}
