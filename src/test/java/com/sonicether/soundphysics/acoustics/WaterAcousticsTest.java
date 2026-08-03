package com.sonicether.soundphysics.acoustics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaterAcousticsTest {

    private static final WaterAcoustics.Settings SETTINGS = WaterAcoustics.Settings.defaults();

    @Test
    void leavesAnAirOnlyPathUnchanged() {
        WaterAcoustics.Effect effect = WaterAcoustics.calculate(path(20.0, 0.0, 0, false), SETTINGS, 0.0f);

        assertEquals(1.0f, effect.directGain(), 1.0E-6f);
        assertEquals(1.0f, effect.highFrequencyGain(), 1.0E-6f);
        assertEquals(1.0f, effect.lowFrequencyGain(), 1.0E-6f);
        assertEquals(0.0f, effect.reflectionSendGain(), 1.0E-6f);
    }

    @Test
    void appliesDistanceDependentHighFrequencyLossOnlyAfterStartDistance() {
        WaterAcoustics.Effect near = WaterAcoustics.calculate(path(10.0, 10.0, 0, true), SETTINGS, 0.0f);
        WaterAcoustics.Effect far = WaterAcoustics.calculate(path(110.0, 110.0, 0, true), SETTINGS, 0.0f);

        assertEquals(1.0f, near.highFrequencyGain(), 1.0E-6f);
        assertEquals(dbToGain(3.0), far.highFrequencyGain(), 1.0E-6f);
        assertEquals(1.0f, far.directGain(), 1.0E-6f);
    }

    @Test
    void capsPathHighFrequencyLoss() {
        WaterAcoustics.Effect effect = WaterAcoustics.calculate(path(1000.0, 1000.0, 0, true), SETTINGS, 0.0f);

        assertEquals(dbToGain(9.0), effect.highFrequencyGain(), 1.0E-6f);
    }

    @Test
    void appliesEachCountedInterfaceAndCapsTheCount() {
        WaterAcoustics.Effect one = WaterAcoustics.calculate(path(5.0, 2.0, 1, false), SETTINGS, 0.0f);
        WaterAcoustics.Effect many = WaterAcoustics.calculate(path(5.0, 2.0, 8, false), SETTINGS, 0.0f);

        assertEquals(dbToGain(12.0), one.directGain(), 1.0E-6f);
        assertEquals(dbToGain(24.0), many.directGain(), 1.0E-6f);
        assertEquals(dbToGain(6.0), many.highFrequencyGain(), 1.0E-6f);
    }

    @Test
    void ignoresUnknownPathSegmentsButRetainsListenerColoration() {
        AcousticPathTracer.Result incomplete = new AcousticPathTracer.Result(30.0, 20.0, 2, false, false, true);
        WaterAcoustics.Effect effect = WaterAcoustics.calculate(incomplete, SETTINGS, 1.0f);

        assertEquals(dbToGain(2.0), effect.directGain(), 1.0E-6f);
        assertEquals(dbToGain(12.0), effect.highFrequencyGain(), 1.0E-6f);
        assertEquals(dbToGain(3.0), effect.lowFrequencyGain(), 1.0E-6f);
        assertEquals(0.0f, effect.reflectionSendGain(), 1.0E-6f);
    }

    @Test
    void listenerColorationIsIndependentFromSourceMedium() {
        WaterAcoustics.Effect effect = WaterAcoustics.calculate(path(5.0, 0.0, 0, false), SETTINGS, 1.0f);

        assertEquals(dbToGain(2.0), effect.directGain(), 1.0E-6f);
        assertEquals(dbToGain(12.0), effect.highFrequencyGain(), 1.0E-6f);
        assertEquals(dbToGain(3.0), effect.lowFrequencyGain(), 1.0E-6f);
    }

    @Test
    void combinesIndependentLossesInLinearGain() {
        WaterAcoustics.Effect effect = WaterAcoustics.calculate(path(110.0, 110.0, 1, true), SETTINGS, 1.0f);

        assertEquals(dbToGain(14.0), effect.directGain(), 1.0E-6f);
        assertEquals(dbToGain(18.0), effect.highFrequencyGain(), 1.0E-6f);
    }

    private static AcousticPathTracer.Result path(double totalLength, double waterLength,
                                                   int interfaces, boolean underwater) {
        return new AcousticPathTracer.Result(totalLength, waterLength, interfaces,
                underwater, underwater, false);
    }

    private static float dbToGain(double lossDb) {
        return (float) Math.pow(10.0, -lossDb / 20.0);
    }
}
