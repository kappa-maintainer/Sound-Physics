package com.sonicether.soundphysics.voicechat;

import com.sonicether.soundphysics.Config;
import com.sonicether.soundphysics.Reference;
import com.sonicether.soundphysics.SoundPhysics;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.ClientLocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

@ForgeVoicechatPlugin
public class SimpleVoiceChatPlugin implements VoicechatPlugin {

    private static final UUID OWN_VOICE_ID = UUID.randomUUID();
    public static String OWN_VOICE_CATEGORY = "own_voice";

    private ClientLocationalAudioChannel locationalAudioChannel;

    public SimpleVoiceChatPlugin() {
    }

    static boolean isOwnVoiceChannel(UUID channelId) {
        return OWN_VOICE_ID.equals(channelId);
    }

    @Override
    public String getPluginId() {
        return Reference.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        SoundPhysics.logger.info("Initializing Simple Voice Chat integration");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientVoicechatConnectionEvent.class, this::onConnection);
        registration.registerEvent(ClientSoundEvent.class, this::onClientSound);
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        VolumeCategory ownVoice = event.getVoicechat().volumeCategoryBuilder()
                .setId(OWN_VOICE_CATEGORY)
                .setName("Own voice")
                .setDescription("The volume of your own voice")
                .build();
        event.getVoicechat().registerVolumeCategory(ownVoice);
    }

    private void onClientSound(ClientSoundEvent event) {
        if (locationalAudioChannel == null) {
            return;
        }
        if (!Config.hearSelf) {
            return;
        }
        if (Minecraft.getMinecraft().player == null) {
            return;
        }
        Vec3d position = Minecraft.getMinecraft().player.getPositionVector().add(0D, Minecraft.getMinecraft().player.getEyeHeight(), 0D);
        locationalAudioChannel.setCategory(OWN_VOICE_CATEGORY);
        locationalAudioChannel.setLocation(event.getVoicechat().createPosition(position.x, position.y, position.z));
        locationalAudioChannel.play(event.getRawAudio());
    }

    private void onConnection(ClientVoicechatConnectionEvent event) {
        if (!event.isConnected()) {
            locationalAudioChannel = null;
            OpenALSpeakerRouter.closeAll();
            return;
        }
        try {
            locationalAudioChannel = event.getVoicechat().createLocationalAudioChannel(OWN_VOICE_ID, event.getVoicechat().createPosition(0D, 0D, 0D));
            SoundPhysics.logger.info("Simple Voice Chat connected, own-voice channel ready (active channels: " + OpenALSpeakerRouter.getActiveChannelCount() + ")");
        } catch (Throwable t) {
            SoundPhysics.logger.error("Failed to create own-voice locational audio channel", t);
            locationalAudioChannel = null;
        }
    }

}
