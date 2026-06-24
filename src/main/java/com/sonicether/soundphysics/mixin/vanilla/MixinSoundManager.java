package com.sonicether.soundphysics.mixin.vanilla;

import com.sonicether.soundphysics.SoundPhysics;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.Sound;
import net.minecraft.client.audio.SoundEventAccessor;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(SoundManager.class)
public class MixinSoundManager {
    
    @Inject(method = "playSound", at = @At("HEAD"))
    private void captureSoundEarly(ISound p_sound, CallbackInfo ci) {
        SoundPhysics.setLastSoundFromISound(p_sound);
    }

    @Inject(method = "playSound", locals = LocalCapture.CAPTURE_FAILSOFT, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/audio/SoundManager$SoundSystemStarterThread;setPitch(Ljava/lang/String;F)V", shift = At.Shift.AFTER))
    private void injectPlaySoundinjectPlaySound(ISound p_sound, CallbackInfo ci, SoundEventAccessor soundeventaccessor, ResourceLocation resourcelocation, Sound sound, float f3, float f, SoundCategory soundcategory, float f1, float f2, boolean flag, String s, ResourceLocation resourcelocation1) {
        SoundPhysics.setLastSound(p_sound, soundcategory, sound.getSoundLocation(), resourcelocation);
    }

    @ModifyArg(method = "playSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/audio/SoundManager$SoundSystemStarterThread;setVolume(Ljava/lang/String;F)V"), index = 1)
    private float modifySetVolume(float value) {
        return SoundPhysics.applyGlobalVolumeMultiplier(value);
    }
}
