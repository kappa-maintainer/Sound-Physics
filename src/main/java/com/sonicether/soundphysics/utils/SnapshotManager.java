package com.sonicether.soundphysics.utils;

import com.sonicether.soundphysics.Config;
import com.sonicether.soundphysics.world.WorldProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class SnapshotManager {

    private int lastDimId = Integer.MAX_VALUE;
    private int lastPlayerChunkX = Integer.MAX_VALUE;
    private int lastPlayerChunkZ = Integer.MAX_VALUE;

    public SnapshotManager() {}

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || !(mc.world instanceof net.minecraft.client.multiplayer.WorldClient)) {
            WorldProxy.clear();
            lastDimId = Integer.MAX_VALUE;
            lastPlayerChunkX = Integer.MAX_VALUE;
            lastPlayerChunkZ = Integer.MAX_VALUE;
            return;
        }
        if (!Config.useSnapshot) {
            if (WorldProxy.get() != null) WorldProxy.clear();
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
        }
    }
}