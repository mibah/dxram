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

import com.google.gson.annotations.Expose;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.hhu.bsinfo.dxcompute.ms.Signal;
import de.hhu.bsinfo.dxcompute.ms.Task;
import de.hhu.bsinfo.dxcompute.ms.TaskContext;
import de.hhu.bsinfo.dxram.chunk.ChunkIDRangeUtils;
import de.hhu.bsinfo.dxram.chunk.ChunkService;
import de.hhu.bsinfo.dxram.data.ChunkID;
import de.hhu.bsinfo.utils.serialization.Exporter;
import de.hhu.bsinfo.utils.serialization.Importer;

public class ChunkRemoveAllTask implements Task {
    private static final Logger LOGGER = LogManager.getFormatterLogger(ChunkRemoveAllTask.class.getSimpleName());

    private static final int PATTERN_LOCAL_ONLY = 0;
    private static final int PATTERN_REMOTE_ONLY_SUCCESSOR = 1;
    private static final int PATTERN_REMOTE_ONLY_RANDOM = 2;
    private static final int PATTERN_REMOTE_LOCAL_MIXED_RANDOM = 3;

    @Expose
    private int m_numThreads = 1;
    @Expose
    private int m_chunkBatch = 10;
    @Expose
    private int m_pattern = PATTERN_LOCAL_ONLY;

    public ChunkRemoveAllTask() {

    }

    @Override
    public int execute(final TaskContext p_ctx) {
        boolean remote = m_pattern > PATTERN_LOCAL_ONLY;

        if (remote && p_ctx.getCtxData().getSlaveNodeIds().length < 2) {
            System.out.println("Not enough slaves (min 2) to execute this task");
            return -1;
        }

        ChunkService chunkService = p_ctx.getDXRAMServiceAccessor().getService(ChunkService.class);

        long activeChunkCount = chunkService.getStatus().getNumberOfActiveChunks();
        // don't remove the index chunk
        activeChunkCount -= 1;

        ArrayList<Long> allChunkRanges = ChunkTaskUtils.getChunkRangesForTestPattern(m_pattern, p_ctx, chunkService);
        long[] chunkCountsPerThread = ChunkIDRangeUtils.distributeChunkCountsToThreads(activeChunkCount, m_numThreads);
        ArrayList<Long>[] chunkRangesPerThread = ChunkIDRangeUtils.distributeChunkRangesToThreads(chunkCountsPerThread, allChunkRanges);

        Thread[] threads = new Thread[m_numThreads];
        long[] timeStart = new long[m_numThreads];
        long[] timeEnd = new long[m_numThreads];

        System.out.printf("Removing all (pattern %d) active chunks (total %d) in batches of %d chunk(s) with %d thread(s)...\n", m_pattern, activeChunkCount,
            m_chunkBatch, m_numThreads);

        for (int i = 0; i < threads.length; i++) {
            int threadIdx = i;
            threads[i] = new Thread(() -> {
                long[] chunkIds = new long[m_chunkBatch];
                long batches = chunkCountsPerThread[threadIdx] / m_chunkBatch;
                long lastBatchRemainder = chunkCountsPerThread[threadIdx] % m_chunkBatch;
                ArrayList<Long> chunkRanges = chunkRangesPerThread[threadIdx];

                if (lastBatchRemainder > 0) {
                    batches++;
                }

                // happens if no chunks were created
                if (!chunkRanges.isEmpty()) {
                    int rangeIdx = 0;
                    long rangeStart = chunkRanges.get(rangeIdx * 2);
                    long rangeEnd = chunkRanges.get(rangeIdx * 2 + 1);
                    long batchChunkCount = m_chunkBatch;

                    timeStart[threadIdx] = System.nanoTime();

                    while (batches > 0) {
                        long chunksInRange = ChunkID.getLocalID(rangeEnd) - ChunkID.getLocalID(rangeStart) + 1;

                        if (chunksInRange >= batchChunkCount) {
                            for (int j = 0; j < chunkIds.length; j++) {
                                chunkIds[j] = rangeStart + j;
                            }

                            chunkService.remove(chunkIds);
                            rangeStart += batchChunkCount;
                        } else {
                            // chunksInRange < m_chunkBatch

                            long fillCount = 0;

                            while (fillCount < batchChunkCount) {
                                for (int j = (int) fillCount; j < chunksInRange; j++) {
                                    chunkIds[j] = rangeStart + j;
                                }

                                fillCount += chunksInRange;

                                rangeIdx++;
                                if (rangeIdx * 2 < chunkRanges.size()) {
                                    rangeStart = chunkRanges.get(rangeIdx * 2);
                                    rangeEnd = chunkRanges.get(rangeIdx * 2 + 1);
                                    chunksInRange = ChunkID.getLocalID(rangeEnd) - ChunkID.getLocalID(rangeStart) + 1;
                                } else {
                                    // invalidate spare chunk ids
                                    for (int j = (int) fillCount; j < chunkIds.length; j++) {
                                        chunkIds[j] = ChunkID.INVALID_ID;
                                    }

                                    break;
                                }
                            }

                            chunkService.remove(chunkIds);
                            rangeStart += batchChunkCount;
                        }

                        batches--;
                    }

                    timeEnd[threadIdx] = System.nanoTime();
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        boolean threadJoinFailed = false;
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (final InterruptedException e) {
                LOGGER.error("Joining thread failed", e);
                threadJoinFailed = true;
            }
        }

        if (threadJoinFailed) {
            return -3;
        }

        System.out.print("Times per thread:");
        for (int i = 0; i < m_numThreads; i++) {
            System.out.printf("\nThread-%d: %f sec", i, (timeEnd[i] - timeStart[i]) / 1000.0 / 1000.0 / 1000.0);
        }
        System.out.println();

        // total time is measured by the slowest thread
        long totalTime = 0;
        for (int i = 0; i < m_numThreads; i++) {
            long time = timeEnd[i] - timeStart[i];
            if (time > totalTime) {
                totalTime = time;
            }
        }

        System.out.printf("Total time: %f sec\n", totalTime / 1000.0 / 1000.0 / 1000.0);
        System.out.printf("Throughput: %f chunks/sec\n", 1000.0 * 1000.0 * 1000.0 / ((double) totalTime / activeChunkCount));

        allChunkRanges = chunkService.getAllLocalChunkIDRanges();

        System.out.print("Available chunk ranges after remove:");
        for (int i = 0; i < allChunkRanges.size(); i += 2) {
            System.out.printf("\n[0x%X, 0x%X]", allChunkRanges.get(i), allChunkRanges.get(i + 1));
        }
        System.out.println();

        return 0;
    }

    @Override
    public void handleSignal(final Signal p_signal) {

    }

    @Override
    public void exportObject(final Exporter p_exporter) {
        p_exporter.writeInt(m_numThreads);
        p_exporter.writeInt(m_chunkBatch);
        p_exporter.writeInt(m_pattern);
    }

    @Override
    public void importObject(final Importer p_importer) {
        m_numThreads = p_importer.readInt();
        m_chunkBatch = p_importer.readInt();
        m_pattern = p_importer.readInt();
    }

    @Override
    public int sizeofObject() {
        return Integer.BYTES * 3;
    }
}