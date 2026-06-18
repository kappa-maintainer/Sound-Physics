package com.sonicether.soundphysics.mixin.ic2c;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.sonicether.soundphysics.SoundPhysics;
import ic2.api.classic.audio.IAudioPosition;
import ic2.api.classic.audio.PositionSpec;
import ic2.core.audio.AudioManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "ic2.core.audio.AudioSourceClient", remap = false)
public class MixinAudioSourceClient {
    @Shadow private PositionSpec soundType;

    @Shadow private IAudioPosition position;

    @Inject(method = "play", at = @At(value = "INVOKE", target = "Lpaulscode/sound/SoundSystem;play(Ljava/lang/String;)V"))
    private void injectPlay(CallbackInfo ci) {
        SoundPhysics.setLastSound(this.soundType.ordinal(), null);
    }

    @Redirect(method = "updateVolume", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    private float applyVolumeModifier(float a, float b) {
        return (float) (SoundPhysics.soundDistanceAllowance * Math.max(a,b));
    }

    @Redirect(method = "updateVolume", at = @At(value = "INVOKE", target = "Lic2/core/audio/AudioManager;getMasterVolume()F"))
    private float cancelMasterVolume(AudioManager instance) {
        return 1.0F;
    }

    @Inject(method = "updateVolume", at = @At(value = "JUMP", opcode = Opcodes.IFEQ, ordinal = 0))
    private void updateVec(EntityPlayer player, CallbackInfo ci, @Local LocalRef<Vec3d> pos) {
        pos.set(SoundPhysics.calculateEntitySoundOffsetVec(pos.get(), player, null));
    }
/*

    @Inject(method = "updateVolume", at = @At(value = "INVOKE_ASSIGN", target = "Lic2/core/util/math/MathUtil;substract(Lnet/minecraft/util/math/Vec3d;F)Lnet/minecraft/util/math/Vec3d;"))
    private void setDistanceZero(EntityPlayer player, CallbackInfo ci, @Local(ordinal = 1) LocalDoubleRef distance, @Share("dis") LocalDoubleRef dis) {
        dis.set(distance.get());
        distance.set(0.0);
    }

    @Inject(method = "updateVolume", at = @At(value = "JUMP", opcode = Opcodes.GOTO, ordinal = 2))
    private void injectHook(EntityPlayer player, CallbackInfo ci, @Local(ordinal = 0) Vec3d pos, @Local LocalIntRef i, @Share("dis") LocalDoubleRef dis) {
        i.set(SoundPhysics.ic2DistanceCheckHook(i.get(), dis.get(), this.position.getPosition(), pos) - 1);
    }*/
}
