package com.sonicether.soundphysics.mixin.vanilla;

import com.sonicether.soundphysics.SoundPhysics;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import paulscode.sound.*;
import paulscode.sound.libraries.ChannelLWJGLOpenAL;
import paulscode.sound.libraries.SourceLWJGLOpenAL;

@Mixin(value = SourceLWJGLOpenAL.class, remap = false)
public class MixinSourceLWJGLOpenAL extends Source {

    @Shadow
    private ChannelLWJGLOpenAL channelOpenAL = null;

    public MixinSourceLWJGLOpenAL(boolean priority, boolean toStream, boolean toLoop, String sourcename, FilenameURL filenameURL, SoundBuffer soundBuffer, float x, float y, float z, int attModel, float distOrRoll, boolean temporary) {
        super(priority, toStream, toLoop, sourcename, filenameURL, soundBuffer, x, y, z, attModel, distOrRoll, temporary);
    }

    @Inject(method = "play", at = @At(value = "INVOKE", target = "Lpaulscode/sound/Channel;play()V"))
    private void beforeChannelPlay(Channel c, CallbackInfo ci) {
        if (SoundPhysics.isSoundBlacklisted() && channelOpenAL != null && channelOpenAL.ALSource != null) {
            attModel = SoundSystemConfig.ATTENUATION_LINEAR;
            distOrRoll = 16.0f;
            AL10.alSourcef(channelOpenAL.ALSource.get(0), AL10.AL_ROLLOFF_FACTOR, 0.0f);
            positionChanged();
        }
    }

    @Inject(method = "play", at = @At(value = "INVOKE", target = "Lpaulscode/sound/Channel;play()V", shift = At.Shift.AFTER))
    private void injectPlay(Channel c, CallbackInfo ci) {
        SoundPhysics.onPlaySound(this.position.x, this.position.y, this.position.z, this.channelOpenAL.ALSource.get(0));
    }
}
