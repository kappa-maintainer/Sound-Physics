package com.sonicether.soundphysics.mixin.vanilla;

import com.sonicether.soundphysics.SoundPhysics;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEvents.class)
public class MixinSoundEvents {

    @Mutable @Shadow @Final
    public static SoundEvent UI_BUTTON_CLICK;

    @Inject(
            method = "<clinit>",
            at = @At(
                    value = "TAIL"
            )
    )
    private static void redirectGetRegisteredSoundEvent(CallbackInfo ci) {
        UI_BUTTON_CLICK = SoundPhysics.CLICK;
    }

}
