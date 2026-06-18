package com.sonicether.soundphysics.mixin.umc;

import cam72cam.mod.MinecraftClient;
import cam72cam.mod.math.Vec3d;
import com.sonicether.soundphysics.SoundPhysics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "cam72cam.mod.sound.ClientSound", remap = false)
public abstract class MixinClientSound {

    @Shadow private Vec3d position;

    @Redirect(method = "isDonePlaying", at = @At(value = "INVOKE", target = "Lcam72cam/mod/math/Vec3d;distanceTo(Lcam72cam/mod/math/Vec3d;)D"), remap = true)
    private double applyModifier(Vec3d instance, Vec3d other) {
        return MinecraftClient.getPlayer().getPosition().distanceTo(this.position) / SoundPhysics.soundDistanceAllowance;
    }

}
