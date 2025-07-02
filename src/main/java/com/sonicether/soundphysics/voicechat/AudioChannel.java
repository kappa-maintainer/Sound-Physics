package com.sonicether.soundphysics.voicechat;

import com.sonicether.soundphysics.SoundPhysics;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;

import javax.annotation.Nullable;
import java.util.UUID;

public class AudioChannel {

    private static final String CATEGORY_TEMPLATE = "voicechat:%s";
    public static final String CATEGORY_VOICECHAT = "voicechat";

    private final UUID channelId;
    private long lastUpdate;
    private Vec3d lastPos;

    public AudioChannel(UUID channelId) {
        this.channelId = channelId;
    }

    public void onSound(int source, @Nullable Vec3d soundPos, boolean auxOnly, @Nullable String category) {
        if (soundPos == null) {
            SoundPhysics.setDefaultEnvironment(source, auxOnly);
            return;
        }

        long time = System.currentTimeMillis();

        if (time - lastUpdate < 500 && (lastPos != null && lastPos.distanceTo(soundPos) < 1D)) {
            return;
        }

        SoundPhysics.setLastSound(SoundCategory.MASTER, String.format(CATEGORY_TEMPLATE, category == null ? CATEGORY_VOICECHAT : category));

        SoundPhysics.onPlaySound((float) soundPos.x, (float) soundPos.y, (float) soundPos.z, source);


        lastUpdate = time;
        lastPos = soundPos;
    }

    public UUID getChannelId() {
        return channelId;
    }

    public boolean canBeRemoved() {
        return System.currentTimeMillis() - lastUpdate > 5_000L;
    }
}