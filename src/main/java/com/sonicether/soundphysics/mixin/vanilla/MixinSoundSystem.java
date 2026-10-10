package com.sonicether.soundphysics.mixin.vanilla;

import com.sonicether.soundphysics.SoundPhysics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import paulscode.sound.SoundSystem;
import paulscode.sound.SoundSystemConfig;

@Mixin(value = SoundSystem.class, remap = false)
public class MixinSoundSystem {

    /**
     * The sound system is torn down on resource reloads (F3+T, resource pack
     * change), which destroys the OpenAL context and invalidates every filter,
     * effect, auxiliary slot and source name cached by this mod. Flag the restart
     * so the replacement sound system drops those caches instead of reusing names
     * that now belong to the dead context (or to unrelated new objects).
     */
    @Inject(method = "cleanup", at = @At("HEAD"))
    private void soundphysics$onCleanup(CallbackInfo ci) {
        SoundPhysics.onSoundSystemShutdown();
    }

    @ModifyArgs(method = "newSource(ZLjava/lang/String;Ljava/lang/String;ZFFFIF)V", at = @At(value = "INVOKE", target = "Lpaulscode/sound/CommandObject;<init>(IZZZLjava/lang/String;Ljava/lang/Object;FFFIF)V"))
    private void redirect1(Args args) {
        if ((int) args.get(9) != SoundSystemConfig.ATTENUATION_NONE /*&& !SoundPhysics.isSoundBlacklisted()*/) {
            args.set(9, SoundPhysics.attenuationModel);
            args.set(10, SoundPhysics.globalRolloffFactor);
        }
    }

    @ModifyArgs(method = "newSource(ZLjava/lang/String;Ljava/net/URL;Ljava/lang/String;ZFFFIF)V", at = @At(value = "INVOKE", target = "Lpaulscode/sound/CommandObject;<init>(IZZZLjava/lang/String;Ljava/lang/Object;FFFIF)V"))
    private void redirect2(Args args) {
        if ((int) args.get(9) != SoundSystemConfig.ATTENUATION_NONE /*&& !SoundPhysics.isSoundBlacklisted()*/) {
            args.set(9, SoundPhysics.attenuationModel);
            args.set(10, SoundPhysics.globalRolloffFactor);
        }
    }

    @ModifyArgs(method = "newStreamingSource(ZLjava/lang/String;Ljava/net/URL;Ljava/lang/String;ZFFFIF)V", at = @At(value = "INVOKE", target = "Lpaulscode/sound/CommandObject;<init>(IZZZLjava/lang/String;Ljava/lang/Object;FFFIF)V"))
    private void redirect3(Args args) {
        if ((int) args.get(9) != SoundSystemConfig.ATTENUATION_NONE && !SoundPhysics.isSoundBlacklisted()) {
            args.set(9, SoundPhysics.attenuationModel);
            args.set(10, SoundPhysics.globalRolloffFactor);
        }
    }
}
