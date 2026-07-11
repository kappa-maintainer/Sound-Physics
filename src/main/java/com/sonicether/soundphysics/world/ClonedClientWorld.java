package com.sonicether.soundphysics.world;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public final class ClonedClientWorld implements IBlockAccess {

    private final Map<ChunkPos, ClonedChunk> chunks = new HashMap<>();
    private final Vec3d playerPos;
    private final long tick;
    private final boolean isRaining;
    private final float rainStrength;
    private final String dimensionName;

    public ClonedClientWorld(WorldClient world, BlockPos origin, long tick, int radius) {
        ChunkProviderClientHelper provider = new ChunkProviderClientHelper(world);
        ChunkPos originChunk = new ChunkPos(origin.getX() >> 4, origin.getZ() >> 4);
        for (int dx = -radius; dx < radius; dx++) {
            for (int dz = -radius; dz < radius; dz++) {
                int cx = originChunk.x + dx, cz = originChunk.z + dz;
                Chunk live = provider.getLoadedChunk(cx, cz);
                if (live == null) continue;
                chunks.put(new ChunkPos(cx, cz), new ClonedChunk(live));
            }
        }
        EntityPlayer player = Minecraft.getMinecraft().player;
        this.playerPos = player != null
            ? new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ)
            : new Vec3d(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5);
        this.tick = tick;
        this.isRaining = world.isRaining();
        this.rainStrength = world.rainingStrength;
        this.dimensionName = world.provider == null ? null : world.provider.getDimensionType().getName();
    }

    public Vec3d getPlayerPos() {
        return playerPos;
    }

    public long getTick() {
        return tick;
    }

    public boolean isRaining() {
        return isRaining;
    }

    public float getRainStrength() {
        return rainStrength;
    }

    public String getDimensionName() {
        return dimensionName;
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        ClonedChunk chunk = chunks.get(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
        return chunk == null ? Blocks.AIR.getDefaultState() : chunk.getBlockState(pos);
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        return getBlockState(pos).getMaterial() == net.minecraft.block.material.Material.AIR;
    }

    @Override
    @Nullable
    public TileEntity getTileEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue) {
        return lightValue;
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        ClonedChunk chunk = chunks.get(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
        if (chunk == null) {
            return net.minecraft.init.Biomes.PLAINS;
        }
        int idx = (pos.getZ() & 15) << 4 | (pos.getX() & 15);
        int id = chunk.getBiomeArray()[idx] & 255;
        if (id == 255) {
            return net.minecraft.init.Biomes.PLAINS;
        }
        Biome b = Biome.getBiome(id);
        return b == null ? net.minecraft.init.Biomes.PLAINS : b;
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        return getBlockState(pos).getStrongPower(this, pos, direction);
    }

    @Override
    public WorldType getWorldType() {
        return WorldType.DEFAULT;
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
        return getBlockState(pos).isSideSolid(this, pos, side);
    }

    private static final class ChunkProviderClientHelper {
        private final WorldClient world;

        ChunkProviderClientHelper(WorldClient world) {
            this.world = world;
        }

        @Nullable
        Chunk getLoadedChunk(int x, int z) {
            return world.getChunkProvider().getLoadedChunk(x, z);
        }
    }
}