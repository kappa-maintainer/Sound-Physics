package com.sonicether.soundphysics.voicechat;

import java.util.UUID;

/**
 * Interface implemented on SVC's {@code JavaSpeakerBase} via mixin to expose
 * the audio channel UUID that the original {@code SpeakerManager.createSpeaker}
 * silently discards in 1.12.2.
 */
public interface VoiceChatSpeaker {

    UUID soundphysics$getChannelId();

    void soundphysics$setChannelId(UUID uuid);
}
