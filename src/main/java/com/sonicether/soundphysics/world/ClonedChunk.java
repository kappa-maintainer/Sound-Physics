package com.sonicether.soundphysics.world;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import javax.annotation.Nullable;

final class ClonedChunk {

    private final ChunkPos pos;
    private final IBlockState[][] blockLayers;
    private final byte[] biomeArray;

    ClonedChunk(Chunk liveChunk) {
        this.pos = new ChunkPos(liveChunk.x, liveChunk.z);
        ExtendedBlockStorage[] live = liveChunk.getBlockStorageArray();
        this.blockLayers = new IBlockState[16][];
        for (int y = 0; y < 16; y++) {
            ExtendedBlockStorage section = live[y];
            if (section == null) continue;
            blockLayers[y] = new IBlockState[4096];
            for (int sy = 0; sy < 16; sy++) {
                for (int sz = 0; sz < 16; sz++) {
                    for (int sx = 0; sx < 16; sx++) {
                        blockLayers[y][sy << 8 | sz << 4 | sx] = section.get(sx, sy, sz);
                    }
                }
            }
        }
        this.biomeArray = liveChunk.getBiomeArray().clone();
    }

    ChunkPos getPos() {
        return pos;
    }

    IBlockState getBlockState(BlockPos p) {
        int y = p.getY();
        if (y < 0 || y >= 256) return Blocks.AIR.getDefaultState();
        int layer = y >> 4;
        IBlockState[] blockLayer = blockLayers[layer];
        if (blockLayer == null) return Blocks.AIR.getDefaultState();
        return blockLayers[layer][(y & 15) << 8 | (p.getZ() & 15) << 4 | (p.getX() & 15)];
    }

    byte[] getBiomeArray() {
        return biomeArray;
    }
}