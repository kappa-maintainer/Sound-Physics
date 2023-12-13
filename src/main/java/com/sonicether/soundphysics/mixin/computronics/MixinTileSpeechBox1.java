package com.sonicether.soundphysics.mixin.computronics;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.sonicether.soundphysics.SoundPhysics;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import pl.asie.computronics.Computronics;
import pl.asie.computronics.tile.TileTapeDrive;

@Mixin(targets = "TileSpeechBox$1")
public class MixinTileSpeechBox1 {

    @Shadow
    @Final
    TileTapeDrive this$0;
    @ModifyReturnValue(method = "getSoundPos()Lnet/minecraft/util/math/Vec3d;", at = @At("RETURN"))
    private Vec3d applyOffset(Vec3d origin) {
        return SoundPhysics.computronicsOffset(origin, this$0, Computronics.speechBox.rotation.FACING);
    }
}
