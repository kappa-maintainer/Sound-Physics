package com.sonicether.soundphysics.acoustics;

import com.sonicether.soundphysics.world.ClonedClientWorld;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;

public final class AcousticPathTracer {

    private static final double EPSILON = 1.0E-9;
    private static final int MAX_STEPS = 32_768;

    private AcousticPathTracer() {
    }

    public static Result trace(IBlockAccess world, Vec3d start, Vec3d end) {
        return trace(start.x, start.y, start.z, end.x, end.y, end.z, (x, y, z) -> {
            BlockPos pos = new BlockPos(x, y, z);
            if (world instanceof ClonedClientWorld && !((ClonedClientWorld) world).containsBlock(pos)) {
                return Medium.UNKNOWN;
            }
            return world.getBlockState(pos).getMaterial() == Material.WATER ? Medium.WATER : Medium.AIR;
        });
    }

    public static Result trace(double startX, double startY, double startZ,
                               double endX, double endY, double endZ, MediumSampler sampler) {
        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;
        double totalLength = Math.sqrt(dx * dx + dy * dy + dz * dz);

        Medium sourceMedium = sampler.mediumAt(floor(startX), floor(startY), floor(startZ));
        Medium listenerMedium = sampler.mediumAt(floor(endX), floor(endY), floor(endZ));
        if (totalLength < EPSILON) {
            return new Result(totalLength, 0.0, 0, sourceMedium == Medium.WATER,
                    listenerMedium == Medium.WATER, sourceMedium == Medium.UNKNOWN);
        }

        int x = floor(startX);
        int y = floor(startY);
        int z = floor(startZ);
        int stepX = sign(dx);
        int stepY = sign(dy);
        int stepZ = sign(dz);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double tMaxX = initialBoundaryT(startX, dx, x, stepX);
        double tMaxY = initialBoundaryT(startY, dy, y, stepY);
        double tMaxZ = initialBoundaryT(startZ, dz, z, stepZ);

        Medium medium = sourceMedium;
        boolean incomplete = medium == Medium.UNKNOWN || listenerMedium == Medium.UNKNOWN;
        double waterLength = 0.0;
        int interfaceCrossings = 0;
        double t = 0.0;
        int steps = 0;

        while (t < 1.0 - EPSILON && steps++ < MAX_STEPS) {
            double nextT = Math.min(1.0, Math.min(tMaxX, Math.min(tMaxY, tMaxZ)));
            if (medium == Medium.WATER) {
                waterLength += Math.max(0.0, nextT - t) * totalLength;
            }
            if (nextT >= 1.0 - EPSILON) {
                t = 1.0;
                break;
            }

            if (Math.abs(tMaxX - nextT) < EPSILON) {
                x += stepX;
                tMaxX += tDeltaX;
            }
            if (Math.abs(tMaxY - nextT) < EPSILON) {
                y += stepY;
                tMaxY += tDeltaY;
            }
            if (Math.abs(tMaxZ - nextT) < EPSILON) {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }

            Medium nextMedium = sampler.mediumAt(x, y, z);
            if (nextMedium == Medium.UNKNOWN) {
                incomplete = true;
            } else if (medium != Medium.UNKNOWN && medium != nextMedium) {
                interfaceCrossings++;
            }
            medium = nextMedium;
            t = nextT;
        }

        if (t < 1.0 - EPSILON) {
            incomplete = true;
        }

        return new Result(totalLength, Math.min(waterLength, totalLength), interfaceCrossings,
                sourceMedium == Medium.WATER, listenerMedium == Medium.WATER, incomplete);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int sign(double value) {
        return value > 0.0 ? 1 : value < 0.0 ? -1 : 0;
    }

    private static double initialBoundaryT(double start, double delta, int cell, int step) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double boundary = step > 0 ? cell + 1.0 : cell;
        double result = (boundary - start) / delta;
        return result < 0.0 ? 0.0 : result;
    }

    public enum Medium {
        AIR,
        WATER,
        UNKNOWN
    }

    @FunctionalInterface
    public interface MediumSampler {
        Medium mediumAt(int x, int y, int z);
    }

    public record Result(double totalLength, double waterLength, int interfaceCrossings,
                         boolean sourceInWater, boolean listenerInWater, boolean incomplete) {
    }
}
