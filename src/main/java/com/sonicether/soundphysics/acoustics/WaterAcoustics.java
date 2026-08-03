package com.sonicether.soundphysics.acoustics;

public final class WaterAcoustics {

    private WaterAcoustics() {
    }

    public static Effect calculate(AcousticPathTracer.Result path, Settings settings, float listenerWetness) {
        boolean completePath = !path.incomplete();
        int interfaces = completePath && settings.interfaceEffectsEnabled()
                ? Math.min(Math.max(path.interfaceCrossings(), 0), settings.maxCountedInterfaces())
                : 0;

        float directGain = dbToGain(settings.interfaceTransmissionLossDb() * interfaces);
        float highFrequencyGain = dbToGain(settings.interfaceHighFrequencyLossDb() * interfaces);

        if (completePath && settings.pathAbsorptionEnabled()) {
            double effectiveWaterLength = Math.max(0.0, path.waterLength() - settings.absorptionStartDistance());
            double pathLossDb = Math.min(settings.highFrequencyLossCapDb(),
                    effectiveWaterLength * settings.highFrequencyLossPerBlockDb());
            highFrequencyGain *= dbToGain(pathLossDb);
        }

        float wetness = clamp(listenerWetness, 0.0f, 1.0f);
        float lowFrequencyGain = 1.0f;
        if (settings.listenerColorationEnabled()) {
            directGain *= dbToGain(settings.listenerGainLossDb() * wetness);
            highFrequencyGain *= dbToGain(settings.listenerHighFrequencyLossDb() * wetness);
            lowFrequencyGain = dbToGain(settings.listenerLowFrequencyLossDb() * wetness);
        }

        float reflectionSendGain = 0.0f;
        if (completePath && interfaces > 0 && settings.interfaceReflectionMix() > 0.0f) {
            float perInterfaceTransmission = dbToGain(settings.interfaceTransmissionLossDb());
            double reflectedEnergy = 1.0 - Math.pow(perInterfaceTransmission * perInterfaceTransmission, interfaces);
            reflectionSendGain = Math.min(settings.interfaceReflectionSendCap(),
                    (float) Math.sqrt(Math.max(0.0, reflectedEnergy)) * settings.interfaceReflectionMix());
        }

        return new Effect(clamp(directGain, 0.0f, 1.0f),
                clamp(highFrequencyGain, 0.0f, 1.0f),
                clamp(lowFrequencyGain, 0.0f, 1.0f),
                clamp(reflectionSendGain, 0.0f, 1.0f));
    }

    private static float dbToGain(double lossDb) {
        return (float) Math.pow(10.0, -Math.max(0.0, lossDb) / 20.0);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Settings(boolean pathAbsorptionEnabled,
                           float highFrequencyLossPerBlockDb,
                           float absorptionStartDistance,
                           float highFrequencyLossCapDb,
                           boolean interfaceEffectsEnabled,
                           float interfaceTransmissionLossDb,
                           float interfaceHighFrequencyLossDb,
                           int maxCountedInterfaces,
                           float interfaceReflectionMix,
                           float interfaceReflectionSendCap,
                           boolean listenerColorationEnabled,
                           float listenerGainLossDb,
                           float listenerHighFrequencyLossDb,
                           float listenerLowFrequencyLossDb,
                           int listenerAttackMs,
                           int listenerReleaseMs) {
        public static Settings defaults() {
            return new Settings(true, 0.03f, 10.0f, 9.0f,
                    true, 12.0f, 3.0f, 2, 0.15f, 0.25f,
                    true, 2.0f, 12.0f, 3.0f, 120, 300);
        }
    }

    public record Effect(float directGain, float highFrequencyGain,
                         float lowFrequencyGain, float reflectionSendGain) {
    }
}
