package com.sonicether.soundphysics.mixin.vanilla;

import com.sonicether.soundphysics.world.WorldProxy;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Invalidates the world snapshot when a new chunk loads on the client.
 * ChunkProviderClient.loadChunk is the client's chunk-load entry; blocks are
 * then written directly through Chunk.readFromNBT (which does not go through
 * Chunk.setBlockState), so chunk loads need this separate hook.
 * <p>
 * Whether this mixin is applied at all is controlled by SPMixinConfigPlugin
 * via Config.useSnapshot; with the live (unsafe) world mode it is not applied.
 */
@Mixin(ChunkProviderClient.class)
public class MixinChunkProviderClient {

    @Inject(method = "loadChunk", at = @At("HEAD"))
    private void soundphysics$invalidateSnapshotOnChunkLoad(int chunkX, int chunkZ,
            CallbackInfoReturnable<Chunk> cir) {
        WorldProxy.markDirty(chunkX, chunkZ);
    }
}
