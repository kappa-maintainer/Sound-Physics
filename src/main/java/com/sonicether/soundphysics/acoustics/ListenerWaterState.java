package com.sonicether.soundphysics.acoustics;

public final class ListenerWaterState {

    private static long lastUpdateNanos;
    private static float wetness;
    private static boolean targetUnderwater;
    private static boolean initialized;

    private ListenerWaterState() {
    }

    public static synchronized void setTarget(boolean underwater, WaterAcoustics.Settings settings) {
        // Publish the new target before advancing so the transition starts on
        // the same client tick that detects entering or leaving water.
        targetUnderwater = underwater;
        advance(System.nanoTime(), settings);
    }

    public static synchronized float getWetness(WaterAcoustics.Settings settings) {
        advance(System.nanoTime(), settings);
        return wetness;
    }

    private static void advance(long now, WaterAcoustics.Settings settings) {
        if (!initialized) {
            initialized = true;
            lastUpdateNanos = now;
            wetness = targetUnderwater ? 1.0f : 0.0f;
            return;
        }

        long elapsedNanos = Math.max(0L, now - lastUpdateNanos);
        lastUpdateNanos = now;
        float target = targetUnderwater ? 1.0f : 0.0f;
        int timeMs = targetUnderwater ? settings.listenerAttackMs() : settings.listenerReleaseMs();
        if (timeMs <= 0) {
            wetness = target;
            return;
        }

        double elapsedMs = elapsedNanos / 1_000_000.0;
        float blend = (float) (1.0 - Math.exp(-elapsedMs / timeMs));
        wetness += (target - wetness) * blend;
        if (Math.abs(target - wetness) < 1.0E-4f) {
            wetness = target;
        }
    }

    public static synchronized void reset() {
        initialized = false;
        targetUnderwater = false;
        lastUpdateNanos = 0L;
        wetness = 0.0f;
    }
}
