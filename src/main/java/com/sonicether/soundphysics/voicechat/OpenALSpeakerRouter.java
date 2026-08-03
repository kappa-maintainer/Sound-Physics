package com.sonicether.soundphysics.voicechat;

import com.sonicether.soundphysics.SoundPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OpenALSpeakerRouter {

    static final String CATEGORY_PREFIX = "voicechat:";

    private static final int SAMPLE_RATE = 48000;
    private static final int MAX_QUEUED_BUFFERS = 6;
    private static final long DIAGNOSTIC_INTERVAL_MS = 5000L;

    private static final Map<UUID, ChannelSource> sources = new ConcurrentHashMap<>();
    private static volatile boolean ready;

    private OpenALSpeakerRouter() {
    }

    public static boolean isReady() {
        return ready;
    }

    public static void markReady() {
        ready = true;
        SoundPhysics.logger.info("Simple Voice Chat OpenAL router is ready");
    }

    public static void play(UUID channelId, short[] monoData, float volume,
                            @Nullable Vec3d position, @Nullable String category, float maxDistance) {
        if (!ready || channelId == null || monoData.length == 0) {
            return;
        }
        try {
            ChannelSource cs = sources.computeIfAbsent(channelId, ChannelSource::new);
            cs.play(monoData, volume, position, category, maxDistance);
        } catch (Throwable t) {
            SoundPhysics.logger.error("OpenALSpeakerRouter play error for channel " + channelId, t);
        }
    }

    public static void close(UUID channelId) {
        if (channelId == null) {
            return;
        }
        ChannelSource cs = sources.remove(channelId);
        if (cs != null) {
            cs.destroy();
        }
    }

    public static void closeAll() {
        for (Map.Entry<UUID, ChannelSource> entry : sources.entrySet()) {
            if (sources.remove(entry.getKey(), entry.getValue())) {
                entry.getValue().destroy();
            }
        }
        SoundPhysics.logger.info("Simple Voice Chat OpenAL router cleared its sources");
    }

    public static int getActiveChannelCount() {
        return sources.size();
    }

    private static final class ChannelSource {
        private final UUID channelId;
        private final int sourceId;
        private final Deque<Integer> bufferPool = new ArrayDeque<>();
        private final Set<Integer> allBuffers = new HashSet<>();

        private long lastEnvUpdate;
        private Vec3d lastEnvPos;
        private long lastDiagnosticLog;
        private boolean closed;

        ChannelSource(UUID channelId) {
            this.channelId = channelId;
            this.sourceId = AL10.alGenSources();
            AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
            AL10.alSourcef(sourceId, AL10.AL_ROLLOFF_FACTOR, 0.0f);
            AL10.alSourcef(sourceId, AL10.AL_REFERENCE_DISTANCE, 0.0f);
            AL10.alSourcef(sourceId, AL10.AL_MAX_DISTANCE, Float.MAX_VALUE);
            AL10.alSourcef(sourceId, AL10.AL_GAIN, 1.0f);
            SoundPhysics.registerVoiceSource(sourceId);
            checkAlError("Failed to create OpenAL source for voice channel " + channelId);
            SoundPhysics.logger.info("Created OpenAL source " + sourceId + " for voice channel " + channelId);
        }

        synchronized void play(short[] monoData, float volume, @Nullable Vec3d position,
                               @Nullable String category, float maxDistance) {
            if (closed) {
                return;
            }
            if (position != null) {
                AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
                AL10.alSource3f(sourceId, AL10.AL_POSITION, (float) position.x, (float) position.y, (float) position.z);
                float distanceVolume = distanceVolume(maxDistance, position);

                // JavaSpeakerBase converts the percentage to dB using 10 * ln(x), which
                // corresponds to sqrt(x) when OpenAL receives a linear amplitude gain.
                float finalGain = (float) Math.sqrt(Math.max(0.0f, volume * distanceVolume));
                AL10.alSourcef(sourceId, AL10.AL_GAIN, finalGain);
                logGain(volume, maxDistance, position, distanceVolume, finalGain);
            } else {
                AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                AL10.alSource3f(sourceId, AL10.AL_POSITION, 0f, 0f, 0f);
                float finalGain = (float) Math.sqrt(Math.max(0.0f, volume));
                AL10.alSourcef(sourceId, AL10.AL_GAIN, finalGain);
                logGain(volume, maxDistance, null, 1.0f, finalGain);
            }

            unqueueProcessed();

            int queued = getQueued();
            if (queued >= MAX_QUEUED_BUFFERS) {
                return;
            }

            int bufferId = obtainBuffer();
            ByteBuffer pcm = toByteBuffer(monoData);
            AL10.alBufferData(bufferId, AL10.AL_FORMAT_MONO16, pcm, SAMPLE_RATE);
            AL10.alSourceQueueBuffers(sourceId, bufferId);

            if (AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                AL10.alSourcePlay(sourceId);
            }

            applyEnvironment(position, category);
        }

        private void applyEnvironment(@Nullable Vec3d position, @Nullable String category) {
            if (position == null) {
                SoundPhysics.setListenerColorationOrDefault(sourceId);
                return;
            }

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null || mc.world == null) {
                SoundPhysics.setDefaultEnvironment(sourceId, false);
                return;
            }

            long now = System.currentTimeMillis();
            if (now - lastEnvUpdate < 50 && lastEnvPos != null && lastEnvPos.distanceTo(position) < 1.0) {
                return;
            }
            SoundPhysics.applyVoiceEnvironment(sourceId, position, category);
            lastEnvUpdate = now;
            lastEnvPos = position;
        }

        private void logGain(float volume, float maxDistance, @Nullable Vec3d position, float distanceGain, float finalGain) {
            long now = System.currentTimeMillis();
            if (now - lastDiagnosticLog < DIAGNOSTIC_INTERVAL_MS) {
                return;
            }
            lastDiagnosticLog = now;
            SoundPhysics.logger.info("SVC gain channel=" + channelId
                    + ", inputVolume=" + volume
                    + ", maxDistance=" + maxDistance
                    + ", position=" + position
                    + ", distanceGain=" + distanceGain
                    + ", finalGain=" + finalGain);
        }

        private void unqueueProcessed() {
            int processed = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_PROCESSED);
            if (processed <= 0) {
                return;
            }
            IntBuffer ib = BufferUtils.createIntBuffer(processed);
            AL10.alSourceUnqueueBuffers(sourceId, ib);
            while (ib.hasRemaining()) {
                bufferPool.add(ib.get());
            }
        }

        private int getQueued() {
            return AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED);
        }

        private int obtainBuffer() {
            Integer pooled = bufferPool.poll();
            if (pooled != null) {
                return pooled;
            }
            int buf = AL10.alGenBuffers();
            allBuffers.add(buf);
            return buf;
        }

        synchronized void destroy() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                AL10.alSourceStop(sourceId);
                AL10.alSourcei(sourceId, AL10.AL_BUFFER, 0);
                SoundPhysics.unregisterVoiceSource(sourceId);
                for (Integer buf : allBuffers) {
                    AL10.alDeleteBuffers(buf);
                }
                allBuffers.clear();
                bufferPool.clear();
                AL10.alDeleteSources(sourceId);
                checkAlError("Error destroying OpenAL source for voice channel " + channelId);
            } catch (Throwable t) {
                SoundPhysics.logger.error("Error destroying OpenAL voice source", t);
            }
        }
    }

    private static float distanceVolume(float maxDistance, Vec3d position) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return 1.0f;
        }
        Vec3d listener = mc.player.getPositionVector().add(0D, mc.player.getEyeHeight(), 0D);
        float distance = (float) Math.min(position.distanceTo(listener), maxDistance);
        return 1.0f - distance / Math.max(maxDistance, 1.0f);
    }

    private static ByteBuffer toByteBuffer(short[] shorts) {
        ByteBuffer bb = ByteBuffer.allocateDirect(shorts.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : shorts) {
            bb.putShort(s);
        }
        bb.flip();
        return bb;
    }

    private static void checkAlError(String context) {
        int err = AL10.alGetError();
        if (err != AL10.AL_NO_ERROR) {
            SoundPhysics.logError(context + " (OpenAL error " + err + ")");
        }
    }
}
