package com.sonicether.soundphysics.mixin.vanilla;

import com.sonicether.soundphysics.Config;
import com.sonicether.soundphysics.world.WorldProxy;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Invalidates the world snapshot as soon as any block changes on the client
 * (break/place/piston/fluid/redstone...), instead of waiting up to
 * snapshotMaxRetainTicks. Chunk loading writes blocks directly through
 * Chunk.readFromNBT, so it does not trigger this hook; chunk loads are covered
 * separately via ChunkEvent.Load in SnapshotManager.
 * <p>
 * Whether this mixin is applied at all is controlled by SPMixinConfigPlugin
 * via Config.useSnapshot; with the live (unsafe) world mode it is not applied.
 */
@Mixin(Chunk.class)
public class MixinChunk {

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void soundphysics$invalidateSnapshotOnBlockChange(BlockPos pos, IBlockState state,
            CallbackInfoReturnable<IBlockState> cir) {
        WorldProxy.markDirty(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
