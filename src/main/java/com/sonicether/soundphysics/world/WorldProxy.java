package com.sonicether.soundphysics.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldProxy {

    private static final AtomicReference<ClonedClientWorld> snapshot = new AtomicReference<>();
    // Chunks whose blocks changed (or that just loaded) on the client while the
    // snapshot scheme is active. The tick handler rebuilds only these chunks.
    private static final Set<ChunkPos> dirtyChunks = ConcurrentHashMap.newKeySet();

    private WorldProxy() {}

    public static void markDirty(int chunkX, int chunkZ) {
        dirtyChunks.add(new ChunkPos(chunkX, chunkZ));
    }

    public static boolean isDirty() {
        return !dirtyChunks.isEmpty();
    }

    public static Set<ChunkPos> consumeDirtyChunks() {
        Set<ChunkPos> dirty = new HashSet<>(dirtyChunks);
        dirtyChunks.clear();
        return dirty;
    }

    public static void refresh(WorldClient world, BlockPos origin, long tick, int radius) {
        ClonedClientWorld newSnap = new ClonedClientWorld(world, origin, tick, radius);
        snapshot.set(newSnap);
    }

    /**
     * Rebuilds only the dirty (or newly visible) chunks of the previous
     * snapshot; unchanged chunks are reused as-is. The finished snapshot is
     * published atomically, so concurrent readers keep seeing a consistent
     * immutable world.
     */
    public static void refreshIncremental(ClonedClientWorld previous, WorldClient world, BlockPos origin,
            long tick, int radius, Set<ChunkPos> dirtyChunks) {
        snapshot.set(new ClonedClientWorld(previous, world, origin, tick, radius, dirtyChunks));
    }

    public static void clear() {
        dirtyChunks.clear();
        snapshot.set(null);
    }

    @Nullable
    public static ClonedClientWorld getSnapshot() {
        return snapshot.get();
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