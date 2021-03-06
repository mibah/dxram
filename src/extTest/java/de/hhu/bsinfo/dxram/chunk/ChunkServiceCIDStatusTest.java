package de.hhu.bsinfo.dxram.chunk;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import de.hhu.bsinfo.dxmem.data.ChunkID;
import de.hhu.bsinfo.dxmem.data.ChunkIDRanges;
import de.hhu.bsinfo.dxram.ClientInstance;
import de.hhu.bsinfo.dxram.DXRAM;
import de.hhu.bsinfo.dxram.DXRAMTestConfiguration;
import de.hhu.bsinfo.dxram.DXRAMJunitRunner;
import de.hhu.bsinfo.dxram.boot.BootService;
import de.hhu.bsinfo.dxram.util.NodeRole;

@RunWith(DXRAMJunitRunner.class)
@DXRAMTestConfiguration(runTestOnNodeIdx = 2,
        nodes = {
                @DXRAMTestConfiguration.Node(nodeRole = NodeRole.SUPERPEER),
                @DXRAMTestConfiguration.Node(nodeRole = NodeRole.PEER),
                @DXRAMTestConfiguration.Node(nodeRole = NodeRole.PEER),
        })
public class ChunkServiceCIDStatusTest {
    @ClientInstance
    private DXRAM m_instance;

    @Test
    public void test() {
        ChunkService chunkService = m_instance.getService(ChunkService.class);
        short remotePeer = ChunkTestUtils.getRemotePeer(m_instance);
        short ownNid = m_instance.getService(BootService.class).getNodeID();

        ChunkIDRanges localRanges = new ChunkIDRanges();
        ChunkIDRanges remoteRanges = new ChunkIDRanges();
        ChunkIDRanges emptyRange = new ChunkIDRanges();

        // index chunk is always there
        localRanges.add(ChunkID.getChunkID(ownNid, 0));
        remoteRanges.add(ChunkID.getChunkID(remotePeer, 0));

        Assert.assertEquals(localRanges, chunkService.cidStatus().getAllLocalChunkIDRanges());
        Assert.assertEquals(remoteRanges, chunkService.cidStatus().getAllLocalChunkIDRanges(remotePeer));

        Assert.assertEquals(emptyRange, chunkService.cidStatus().getAllMigratedChunkIDRanges());
        Assert.assertEquals(emptyRange, chunkService.cidStatus().getAllMigratedChunkIDRanges(remotePeer));
    }
}
