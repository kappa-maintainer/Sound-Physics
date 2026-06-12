package com.sonicether.soundphysics.mixin.vanilla;

import com.sonicether.soundphysics.SoundPhysics;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "setIngameFocus", at = @At("HEAD"))
    private void log(CallbackInfo ci) {
        Arrays.stream(Thread.currentThread().getStackTrace()).forEach(SoundPhysics.logger::info);
    }
}
