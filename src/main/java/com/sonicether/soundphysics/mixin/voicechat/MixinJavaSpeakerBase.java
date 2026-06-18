package com.sonicether.soundphysics.mixin.voicechat;

import com.sonicether.soundphysics.Config;
import com.sonicether.soundphysics.voicechat.OpenALSpeakerRouter;
import com.sonicether.soundphysics.voicechat.VoiceChatSpeaker;
import de.maxhenkel.voicechat.voice.client.speaker.JavaSpeakerBase;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.UUID;

@Mixin(value = JavaSpeakerBase.class, remap = false)
public class MixinJavaSpeakerBase implements VoiceChatSpeaker {

    @Unique
    private UUID soundphysics$channelId;

    @Override
    public UUID soundphysics$getChannelId() {
        return soundphysics$channelId;
    }

    @Override
    public void soundphysics$setChannelId(UUID uuid) {
        soundphysics$channelId = uuid;
    }

    @Inject(method = "play([SFLnet/minecraft/util/math/Vec3d;Ljava/lang/String;F)V", at = @At("HEAD"), cancellable = true)
    private void soundphysics$onPlay(short[] data, float volume, @Nullable Vec3d position,
                                     @Nullable String category, float maxDistance, CallbackInfo ci) {
        if (!Config.simpleVoiceChatIntegration || !OpenALSpeakerRouter.isReady() || soundphysics$channelId == null) {
            return;
        }
        ci.cancel();
        OpenALSpeakerRouter.play(soundphysics$channelId, data, volume, position, category, maxDistance);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void soundphysics$onClose(CallbackInfo ci) {
        if (soundphysics$channelId != null) {
            OpenALSpeakerRouter.close(soundphysics$channelId);
        }
    }
}
