package com.sonicether.soundphysics.world;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;

import javax.annotation.Nullable;

final class UnsafeClientWorld implements IBlockAccess {

    private final WorldClient world;

    UnsafeClientWorld(WorldClient world) {
        this.world = world;
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return world.getBlockState(pos);
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        return world.isAirBlock(pos);
    }

    @Override
    @Nullable
    public TileEntity getTileEntity(BlockPos pos) {
        return world.getTileEntity(pos);
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue) {
        return world.getCombinedLight(pos, lightValue);
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return world.getBiome(pos);
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        return world.getStrongPower(pos, direction);
    }

    @Override
    public WorldType getWorldType() {
        return world.getWorldType();
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
        return world.isSideSolid(pos, side, _default);
    }
}