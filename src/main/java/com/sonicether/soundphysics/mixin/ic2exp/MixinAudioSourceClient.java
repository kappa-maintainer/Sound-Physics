package com.sonicether.soundphysics.mixin.ic2exp;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.sonicether.soundphysics.SoundPhysics;
import ic2.core.audio.AudioManager;
import ic2.core.audio.AudioPosition;
import ic2.core.audio.PositionSpec;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "ic2.core.audio.AudioSourceClient", remap = false)
public class MixinAudioSourceClient {


    @Shadow @Final private PositionSpec positionSpec;

    @Shadow @Final private String initialSoundFile;

    @Shadow private AudioPosition position;

    @Inject(method = "play", at = @At(value = "INVOKE", target = "Lpaulscode/sound/SoundSystem;play(Ljava/lang/String;)V"))
    private void injectPlay(CallbackInfo ci) {
        SoundPhysics.setLastSound(this.positionSpec.ordinal(), this.initialSoundFile);
    }

    @Redirect(method = "updateVolume", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    private float applyVolumeModifier(float a, float b) {
        return (float) (SoundPhysics.soundDistanceAllowance * Math.max(a,b));
    }

    @Redirect(method = "updateVolume", at = @At(value = "INVOKE", target = "Lic2/core/audio/AudioManager;getMasterVolume()F"))
    private float cancelMasterVolume(AudioManager instance) {
        return 1.0F;
    }

    @Inject(method = "updateVolume", at = @At(value = "JUMP", opcode = Opcodes.IFNULL, ordinal = 0))
    private void updateVec(EntityPlayer player, CallbackInfo ci, @Local(ordinal = 4)LocalFloatRef y) {
        y.set((float) (y.get() + SoundPhysics.calculateEntitySoundOffset(player, null)));
    }


    @Inject(method = "updateVolume", at = @At(value = "JUMP", opcode = Opcodes.IFLE, ordinal = 1))
    private void setDistanceZero(EntityPlayer player, CallbackInfo ci, @Local(ordinal = 5) LocalDoubleRef distance, @Share("dis") LocalDoubleRef dis) {
        dis.set(distance.get());
        distance.set(0.0);
    }

    @Inject(method = "updateVolume", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, ordinal = 29, shift = At.Shift.BY, by = -4))
    private void injectHook(EntityPlayer player, CallbackInfo ci, @Local LocalIntRef i, @Local(ordinal = 3) float x, @Local(ordinal = 4) float y, @Local(ordinal = 5) float z, @Share("dis") LocalDoubleRef dis) {
        i.set(SoundPhysics.ic2DistanceCheckHook((float) i.get(), (float) dis.get(), this.position.x, this.position.y, this.position.z, x, y, z) - 1);
    }
}
