package com.sonicether.soundphysics.utils;

import com.sonicether.soundphysics.ClientHelper;
import com.sonicether.soundphysics.Config;
import com.sonicether.soundphysics.SoundPhysics;
import com.sonicether.soundphysics.acoustics.ListenerWaterState;
import com.sonicether.soundphysics.acoustics.WaterAcoustics;
import com.sonicether.soundphysics.world.ClonedClientWorld;
import com.sonicether.soundphysics.world.WorldProxy;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Set;

public final class SnapshotManager {

    // Minimum interval between block-change-driven snapshot rebuilds. Prevents
    // rapidly-changing areas (fluids, redstone) from rebuilding every tick.
    private static final long DIRTY_REFRESH_COOLDOWN_NANOS = 250_000_000L;

    private int lastDimId = Integer.MAX_VALUE;
    private int lastPlayerChunkX = Integer.MAX_VALUE;
    private int lastPlayerChunkZ = Integer.MAX_VALUE;
    private boolean lastListenerInWater;
    private float lastPublishedWetness;
    private long lastWaterEnvironmentRefreshNanos;
    private long lastDirtyRefreshNanos;

    public SnapshotManager() {}

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || !(mc.world instanceof net.minecraft.client.multiplayer.WorldClient)) {
            WorldProxy.clear();
            ListenerWaterState.reset();
            lastDimId = Integer.MAX_VALUE;
            lastPlayerChunkX = Integer.MAX_VALUE;
            lastPlayerChunkZ = Integer.MAX_VALUE;
            lastListenerInWater = false;
            lastPublishedWetness = 0.0f;
            lastWaterEnvironmentRefreshNanos = 0L;
            return;
        }

        WaterAcoustics.Settings waterSettings = Config.getWaterAcoustics();
        boolean listenerInWater = ClientHelper.isInsideOfMaterial(Material.WATER);
        ListenerWaterState.setTarget(listenerInWater, waterSettings);
        float wetness = ListenerWaterState.getWetness(waterSettings);
        boolean listenerStateChanged = listenerInWater != lastListenerInWater;
        lastListenerInWater = listenerInWater;
        long nowNanos = System.nanoTime();
        if (listenerStateChanged || Math.abs(wetness - lastPublishedWetness) >= 0.001f
                && nowNanos - lastWaterEnvironmentRefreshNanos >= 50_000_000L) {
            lastPublishedWetness = wetness;
            lastWaterEnvironmentRefreshNanos = nowNanos;
            SoundPhysics.refreshListenerWaterEnvironment();
            if (Config.useSnapshot) {
                doRefresh(mc);
            }
        }
        if (!Config.useSnapshot) {
            if (WorldProxy.get() != null) WorldProxy.clear();
            return;
        }

        // Block changes (break/place/updates) and newly loaded chunks invalidate
        // the snapshot immediately instead of waiting up to
        // snapshotMaxRetainTicks. Only the affected chunks are re-cloned; a
        // short cooldown prevents rapidly-changing areas (fluids, redstone)
        // from rebuilding too often.
        if (WorldProxy.isDirty() && nowNanos - lastDirtyRefreshNanos >= DIRTY_REFRESH_COOLDOWN_NANOS) {
            lastDirtyRefreshNanos = nowNanos;
            Set<ChunkPos> dirty = WorldProxy.consumeDirtyChunks();
            BlockPos origin = new BlockPos(mc.player);
            ClonedClientWorld previous = WorldProxy.getSnapshot();
            if (previous == null) {
                WorldProxy.refresh(mc.world, origin, mc.world.getWorldTime(), Config.snapshotRange);
            } else {
                WorldProxy.refreshIncremental(previous, mc.world, origin, mc.world.getWorldTime(),
                        Config.snapshotRange, dirty);
            }
            return;
        }

        int curDim = mc.world.provider.getDimensionType().getId();
        if (curDim != lastDimId) {
            lastDimId = curDim;
            WorldProxy.clear();
            doRefresh(mc);
            return;
        }

        int pcx = (int) mc.player.posX >> 4;
        int pcz = (int) mc.player.posZ >> 4;
        if (pcx != lastPlayerChunkX || pcz != lastPlayerChunkZ) {
            lastPlayerChunkX = pcx;
            lastPlayerChunkZ = pcz;
            doRefresh(mc);
            return;
        }

        if (mc.world.getWorldTime() - WorldProxy.getTick() > Config.snapshotMaxRetainTicks) {
            doRefresh(mc);
        }
    }

    private void doRefresh(Minecraft mc) {
        BlockPos origin = new BlockPos(mc.player);
        WorldProxy.refresh(mc.world, origin,
            mc.world.getWorldTime(), Config.snapshotRange);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player == Minecraft.getMinecraft().player) {
            WorldProxy.clear();
            ListenerWaterState.reset();
        }
    }
}