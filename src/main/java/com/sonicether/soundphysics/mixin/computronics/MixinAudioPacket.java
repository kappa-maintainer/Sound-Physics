package com.sonicether.soundphysics.mixin.computronics;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.sonicether.soundphysics.SoundPhysics;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.asie.computronics.api.audio.AudioPacket;
import pl.asie.computronics.api.audio.IAudioReceiver;

@Mixin(value = AudioPacket.class, remap = false)
public class MixinAudioPacket {
    @Inject(method = "canHearReceiver", at = @At(value = "INVOKE", target = "Lpl/asie/computronics/api/audio/IAudioReceiver;getSoundPos()Lnet/minecraft/util/math/Vec3d;"))
    private void injectDistance(EntityPlayerMP playerMP, IAudioReceiver receiver, CallbackInfoReturnable<Boolean> cir, @Local LocalIntRef mdsq) {
        mdsq.set(mdsq.get() * (int) (SoundPhysics.soundDistanceAllowance));
    }
}
