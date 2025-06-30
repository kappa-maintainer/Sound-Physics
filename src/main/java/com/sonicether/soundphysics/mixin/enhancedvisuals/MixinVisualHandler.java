package com.sonicether.soundphysics.mixin.enhancedvisuals;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import team.creative.enhancedvisuals.api.VisualHandler;

@Mixin(value = VisualHandler.class, remap = false)
public class MixinVisualHandler {
    @ModifyVariable(method = "playSound(Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/util/math/BlockPos;F)V", at = @At("HEAD"), order = 0, argsOnly = true)
    private BlockPos fixnull(BlockPos value) {
        if (value == null) {
            return Minecraft.getMinecraft().player.getPosition();
        } else {
            return value;
        }
    }

    @ModifyVariable(method = "playSoundFadeOut", at = @At("HEAD"), order = 0, argsOnly = true)
    private BlockPos fixnull2(BlockPos value) {
        if (value == null) {
            return Minecraft.getMinecraft().player.getPosition();
        } else {
            return value;
        }
    }
}
