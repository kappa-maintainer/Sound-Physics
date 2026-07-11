package com.sonicether.soundphysics.utils;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;

public final class RaycastHelper {

    private RaycastHelper() {}

    public static RayTraceResult rayTraceBlocks(IBlockAccess world, Vec3d start, Vec3d end, boolean stopOnLiquid) {
        return rayTraceBlocks(world, start, end, stopOnLiquid, false, false);
    }

    public static RayTraceResult rayTraceBlocks(IBlockAccess world, Vec3d start, Vec3d end,
        boolean stopOnLiquid, boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock) {
        if (Double.isNaN(start.x) || Double.isNaN(start.y) || Double.isNaN(start.z)) {
            return null;
        } else if (!Double.isNaN(end.x) && !Double.isNaN(end.y) && !Double.isNaN(end.z)) {
            int i = MathHelper.floor(end.x);
            int j = MathHelper.floor(end.y);
            int k = MathHelper.floor(end.z);
            int l = MathHelper.floor(start.x);
            int i1 = MathHelper.floor(start.y);
            int j1 = MathHelper.floor(start.z);
            BlockPos blockpos = new BlockPos(l, i1, j1);
            IBlockState iblockstate = world.getBlockState(blockpos);
            Block block = iblockstate.getBlock();

            if ((!ignoreBlockWithoutBoundingBox || iblockstate.getCollisionBoundingBox(world, blockpos) != Block.NULL_AABB) && block.canCollideCheck(iblockstate, stopOnLiquid)) {
                RayTraceResult raytraceresult = collisionRayTrace(world, iblockstate, blockpos, start, end);
                if (raytraceresult != null) return raytraceresult;
            }

            RayTraceResult raytraceresult2 = null;
            int k1 = 200;
            Vec3d vec31 = start;
            Vec3d vec32 = end;

            while (k1-- >= 0) {
                if (Double.isNaN(vec31.x) || Double.isNaN(vec31.y) || Double.isNaN(vec31.z)) {
                    return null;
                }
                if (l == i && i1 == j && j1 == k) {
                    return returnLastUncollidableBlock ? raytraceresult2 : null;
                }

                boolean flag2 = true, flag = true, flag1 = true;
                double d0 = 999.0, d1 = 999.0, d2 = 999.0;

                if (i > l) d0 = l + 1.0;
                else if (i < l) d0 = l + 0.0;
                else flag2 = false;

                if (j > i1) d1 = i1 + 1.0;
                else if (j < i1) d1 = i1 + 0.0;
                else flag = false;

                if (k > j1) d2 = j1 + 1.0;
                else if (k < j1) d2 = j1 + 0.0;
                else flag1 = false;

                double d3 = 999.0, d4 = 999.0, d5 = 999.0;
                double d6 = vec32.x - vec31.x;
                double d7 = vec32.y - vec31.y;
                double d8 = vec32.z - vec31.z;

                if (flag2) d3 = (d0 - vec31.x) / d6;
                if (flag) d4 = (d1 - vec31.y) / d7;
                if (flag1) d5 = (d2 - vec31.z) / d8;

                if (d3 == -0.0) d3 = -1.0E-4;
                if (d4 == -0.0) d4 = -1.0E-4;
                if (d5 == -0.0) d5 = -1.0E-4;

                EnumFacing facing;
                if (d3 < d4 && d3 < d5) {
                    facing = i > l ? EnumFacing.WEST : EnumFacing.EAST;
                    vec31 = new Vec3d(d0, vec31.y + d7 * d3, vec31.z + d8 * d3);
                } else if (d4 < d5) {
                    facing = j > i1 ? EnumFacing.DOWN : EnumFacing.UP;
                    vec31 = new Vec3d(vec31.x + d6 * d4, d1, vec31.z + d8 * d4);
                } else {
                    facing = k > j1 ? EnumFacing.NORTH : EnumFacing.SOUTH;
                    vec31 = new Vec3d(vec31.x + d6 * d5, vec31.y + d7 * d5, d2);
                }

                l = MathHelper.floor(vec31.x) - (facing == EnumFacing.EAST ? 1 : 0);
                i1 = MathHelper.floor(vec31.y) - (facing == EnumFacing.UP ? 1 : 0);
                j1 = MathHelper.floor(vec31.z) - (facing == EnumFacing.SOUTH ? 1 : 0);
                blockpos = new BlockPos(l, i1, j1);
                IBlockState iblockstate1 = world.getBlockState(blockpos);
                Block block1 = iblockstate1.getBlock();

                if (!ignoreBlockWithoutBoundingBox || iblockstate1.getMaterial() == Material.PORTAL || iblockstate1.getCollisionBoundingBox(world, blockpos) != Block.NULL_AABB) {
                    if (block1.canCollideCheck(iblockstate1, stopOnLiquid)) {
                        RayTraceResult raytraceresult1 = collisionRayTrace(world, iblockstate1, blockpos, vec31, vec32);
                        if (raytraceresult1 != null) return raytraceresult1;
                    } else {
                        raytraceresult2 = new RayTraceResult(RayTraceResult.Type.MISS, vec31, facing, blockpos);
                    }
                }
            }

            return returnLastUncollidableBlock ? raytraceresult2 : null;
        } else {
            return null;
        }
    }

    private static RayTraceResult collisionRayTrace(IBlockAccess world, IBlockState state, BlockPos pos, Vec3d start, Vec3d end) {
        AxisAlignedBB box = state.getBoundingBox(world, pos);
        return rayTrace(pos, start, end, box);
    }

    private static RayTraceResult rayTrace(BlockPos pos, Vec3d start, Vec3d end, AxisAlignedBB boundingBox) {
        Vec3d vec3d = start.subtract(pos.getX(), pos.getY(), pos.getZ());
        Vec3d vec3d1 = end.subtract(pos.getX(), pos.getY(), pos.getZ());
        RayTraceResult raytraceresult = boundingBox.calculateIntercept(vec3d, vec3d1);
        return raytraceresult == null ? null
            : new RayTraceResult(raytraceresult.hitVec.add(pos.getX(), pos.getY(), pos.getZ()),
                raytraceresult.sideHit, pos);
    }
}