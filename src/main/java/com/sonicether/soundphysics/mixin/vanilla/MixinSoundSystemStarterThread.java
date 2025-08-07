package com.sonicether.soundphysics.mixin.vanilla;

import com.sonicether.soundphysics.SoundPhysics;
import net.minecraft.client.audio.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import paulscode.sound.SoundSystem;

@Mixin(targets = "net.minecraft.client.audio.SoundManager$SoundSystemStarterThread", remap = false)
public class MixinSoundSystemStarterThread {
    @Inject(method = "<init>(Lnet/minecraft/client/audio/SoundManager;)V", at = @At("TAIL"))
    private void injectInit(SoundManager p_i45117_1_, CallbackInfo ci) {
        SoundPhysics.init((SoundSystem)(Object)this);
    }

}
