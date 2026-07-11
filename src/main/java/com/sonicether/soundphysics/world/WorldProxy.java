package com.sonicether.soundphysics.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldProxy {

    private static final AtomicReference<ClonedClientWorld> snapshot = new AtomicReference<>();

    private WorldProxy() {}

    public static void refresh(WorldClient world, BlockPos origin, long tick, int radius) {
        ClonedClientWorld newSnap = new ClonedClientWorld(world, origin, tick, radius);
        snapshot.set(newSnap);
    }

    public static void clear() {
        snapshot.set(null);
    }

    @Nullable
    public static IBlockAccess get() {
        return snapshot.get();
    }

    public static long getTick() {
        ClonedClientWorld cs = snapshot.get();
        return cs != null ? cs.getTick() : -1;
    }

    @Nullable
    public static Vec3d getPlayerPos() {
        ClonedClientWorld cs = snapshot.get();
        return cs != null ? cs.getPlayerPos() : null;
    }

    @Nullable
    public static IBlockAccess getOrFallback() {
        IBlockAccess live = snapshot.get();
        if (live != null) return live;
        WorldClient world = Minecraft.getMinecraft().world;
        return world != null ? new UnsafeClientWorld(world) : null;
    }
}