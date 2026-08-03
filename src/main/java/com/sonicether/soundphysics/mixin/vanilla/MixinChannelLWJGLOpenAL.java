package com.sonicether.soundphysics.mixin.vanilla;

import com.sonicether.soundphysics.SoundPhysics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import paulscode.sound.libraries.ChannelLWJGLOpenAL;

@Mixin(value = ChannelLWJGLOpenAL.class, remap = false)
public class MixinChannelLWJGLOpenAL {

    @Inject(method = {"stop", "close", "cleanup"}, at = @At("HEAD"))
    private void soundphysics$releaseFilters(CallbackInfo ci) {
        ChannelLWJGLOpenAL self = (ChannelLWJGLOpenAL) (Object) this;
        if (self.ALSource != null && self.ALSource.capacity() > 0) {
            SoundPhysics.releaseSource(self.ALSource.get(0));
        }
    }
}
