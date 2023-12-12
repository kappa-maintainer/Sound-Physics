package com.sonicether.soundphysics.mixin.vanilla;

import com.sonicether.soundphysics.SoundPhysics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import paulscode.sound.SoundSystem;

@Mixin(value = SoundSystem.class, remap = false)
public class MixinSoundSystem {
    @ModifyArgs(method = "newSource(ZLjava/lang/String;Ljava/lang/String;ZFFFIF)V", at = @At(value = "INVOKE", target = "Lpaulscode/sound/CommandObject;<init>(IZZZLjava/lang/String;Ljava/lang/Object;FFFIF)V"))
    private void redirect1(Args args) {
        args.set(9, SoundPhysics.attenuationModel);
        args.set(10, SoundPhysics.globalRolloffFactor);
    }

    @ModifyArgs(method = "newSource(ZLjava/lang/String;Ljava/net/URL;Ljava/lang/String;ZFFFIF)V", at = @At(value = "INVOKE", target = "Lpaulscode/sound/CommandObject;<init>(IZZZLjava/lang/String;Ljava/lang/Object;FFFIF)V"))
    private void redirect2(Args args) {
        args.set(9, SoundPhysics.attenuationModel);
        args.set(10, SoundPhysics.globalRolloffFactor);
    }
}
