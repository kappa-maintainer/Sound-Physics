package com.sonicether.soundphysics.mixin.voicechat;

import com.sonicether.soundphysics.voicechat.VoiceChatSpeaker;
import de.maxhenkel.voicechat.voice.client.SoundManager;
import de.maxhenkel.voicechat.voice.client.speaker.Speaker;
import de.maxhenkel.voicechat.voice.client.speaker.SpeakerException;
import de.maxhenkel.voicechat.voice.client.speaker.SpeakerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.UUID;

@Mixin(value = SpeakerManager.class, remap = false)
public class MixinSpeakerManager {

    @Inject(method = "createSpeaker", at = @At("RETURN"))
    private static void soundphysics$setChannelId(SoundManager soundManager, @Nullable UUID audioChannel,
                                                  CallbackInfoReturnable<Speaker> cir) {
        Speaker speaker = cir.getReturnValue();
        if (speaker instanceof VoiceChatSpeaker && audioChannel != null) {
            ((VoiceChatSpeaker) speaker).soundphysics$setChannelId(audioChannel);
        }
    }
}
