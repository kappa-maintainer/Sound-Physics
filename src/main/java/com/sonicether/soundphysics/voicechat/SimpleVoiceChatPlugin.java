package com.sonicether.soundphysics.voicechat;

import com.sonicether.soundphysics.Config;
import com.sonicether.soundphysics.SoundPhysics;
import com.sonicether.soundphysics.Tags;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.ClientLocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.CreateOpenALContextEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCcontext;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ForgeVoicechatPlugin
public class SimpleVoiceChatPlugin implements VoicechatPlugin {

    private static final UUID OWN_VOICE_ID = UUID.randomUUID();
    public static String OWN_VOICE_CATEGORY = "own_voice";

    private final Map<UUID, AudioChannel> audioChannels;
    private ClientLocationalAudioChannel locationalAudioChannel;

    private static final Constructor<?> cotr;

    static {
        try {
            cotr = ALCcontext.class.getDeclaredConstructor(long.class);
            cotr.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public SimpleVoiceChatPlugin() {
        audioChannels = new HashMap<>();
    }

    @Override
    public String getPluginId() {
        return Tags.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        SoundPhysics.logger.info("Initializing Simple Voice Chat integration");
        audioChannels.clear();
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(CreateOpenALContextEvent.class, this::onCreateALContext);
        registration.registerEvent(OpenALSoundEvent.class, this::onOpenALSound);
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
        Vec3d position = Minecraft.getMinecraft().player.getPositionVector();
        locationalAudioChannel.setCategory(OWN_VOICE_CATEGORY);
        locationalAudioChannel.setLocation(event.getVoicechat().createPosition(position.x, position.y, position.z));
        locationalAudioChannel.play(event.getRawAudio());
    }

    private void onCreateALContext(CreateOpenALContextEvent event){
        ALCcontext oldContext = ALC10.alcGetCurrentContext();
        try {
            ALC10.alcMakeContextCurrent((ALCcontext) cotr.newInstance(event.getContext()));
        } catch (Throwable t) {
            SoundPhysics.logger.error(t);
        }

        SoundPhysics.logger.info("Initializing sound physics for voice chat audio");
        SoundPhysics.init();

        ALC10.alcMakeContextCurrent(oldContext);
    }

    private void onConnection(ClientVoicechatConnectionEvent event) {
        SoundPhysics.logger.debug("Clearing unused audio channels");
        audioChannels.values().removeIf(AudioChannel::canBeRemoved);
        locationalAudioChannel = event.getVoicechat().createLocationalAudioChannel(OWN_VOICE_ID, event.getVoicechat().createPosition(0D, 0D, 0D));
    }

    private void onOpenALSound(OpenALSoundEvent event) {
        if (!Config.simpleVoiceChatIntegration) {
            return;
        }

        @Nullable Position position = event.getPosition();
        @Nullable UUID channelId = event.getChannelId();

        if (channelId == null) {
            return;
        }

        boolean auxOnly = Config.hearSelf && OWN_VOICE_ID.equals(channelId);

        @Nullable AudioChannel audioChannel = audioChannels.computeIfAbsent(channelId, AudioChannel::new);

        audioChannel.onSound(event.getSource(), position == null ? null : new Vec3d(position.getX(), position.getY(), position.getZ()), auxOnly, event.getCategory());
    }

}
