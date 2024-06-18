package com.sonicether.soundphysics.mixin.umc;

import cam72cam.mod.MinecraftClient;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.resource.Identifier;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.sonicether.soundphysics.SoundPhysics;
import net.minecraft.util.SoundCategory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import paulscode.sound.SoundSystem;

import java.util.function.Supplier;

@Mixin(targets = "cam72cam.mod.sound.ClientSound", remap = false)
public abstract class MixinClientSound {

    @Shadow private Vec3d position;

    @Redirect(method = "isDonePlaying", at = @At(value = "INVOKE", target = "Lcam72cam/mod/math/Vec3d;distanceTo(Lcam72cam/mod/math/Vec3d;)D"), remap = true)
    private double applyModifier(Vec3d instance, Vec3d other) {
        return MinecraftClient.getPlayer().getPosition().distanceTo(this.position) / SoundPhysics.soundDistanceAllowance;
    }

}
