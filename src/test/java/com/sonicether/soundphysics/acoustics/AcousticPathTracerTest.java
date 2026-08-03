package com.sonicether.soundphysics.acoustics;

import org.junit.jupiter.api.Test;

import static com.sonicether.soundphysics.acoustics.AcousticPathTracer.Medium.AIR;
import static com.sonicether.soundphysics.acoustics.AcousticPathTracer.Medium.UNKNOWN;
import static com.sonicether.soundphysics.acoustics.AcousticPathTracer.Medium.WATER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcousticPathTracerTest {

    @Test
    void tracesAirWithoutWaterEffects() {
        AcousticPathTracer.Result result = AcousticPathTracer.trace(
                0.5, 0.5, 0.5, 10.5, 0.5, 0.5, (x, y, z) -> AIR);

        assertEquals(10.0, result.totalLength(), 1.0E-9);
        assertEquals(0.0, result.waterLength(), 1.0E-9);
        assertEquals(0, result.interfaceCrossings());
        assertFalse(result.sourceInWater());
        assertFalse(result.listenerInWater());
        assertFalse(result.incomplete());
    }

    @Test
    void measuresWaterLengthAndSingleInterface() {
        AcousticPathTracer.Result result = AcousticPathTracer.trace(
                0.5, 0.5, 0.5, 10.5, 0.5, 0.5, (x, y, z) -> x < 5 ? WATER : AIR);

        assertEquals(4.5, result.waterLength(), 1.0E-9);
        assertEquals(1, result.interfaceCrossings());
        assertTrue(result.sourceInWater());
        assertFalse(result.listenerInWater());
    }

    @Test
    void countsBothSidesOfWaterWall() {
        AcousticPathTracer.Result result = AcousticPathTracer.trace(
                0.5, 0.5, 0.5, 8.5, 0.5, 0.5, (x, y, z) -> x >= 3 && x < 5 ? WATER : AIR);

        assertEquals(2.0, result.waterLength(), 1.0E-9);
        assertEquals(2, result.interfaceCrossings());
        assertFalse(result.sourceInWater());
        assertFalse(result.listenerInWater());
    }

    @Test
    void measuresDiagonalSegmentsByDistance() {
        double diagonal = Math.sqrt(2.0) * 4.0;
        AcousticPathTracer.Result result = AcousticPathTracer.trace(
                0.5, 0.5, 0.5, 4.5, 4.5, 0.5, (x, y, z) -> x < 2 ? WATER : AIR);

        assertEquals(diagonal, result.totalLength(), 1.0E-9);
        assertEquals(Math.sqrt(2.0) * 1.5, result.waterLength(), 1.0E-9);
        assertEquals(1, result.interfaceCrossings());
    }

    @Test
    void tracesNegativeDirectionAcrossInterfaces() {
        AcousticPathTracer.Result result = AcousticPathTracer.trace(
                8.5, 0.5, 0.5, 0.5, 0.5, 0.5, (x, y, z) -> x >= 3 && x < 5 ? WATER : AIR);

        assertEquals(2.0, result.waterLength(), 1.0E-9);
        assertEquals(2, result.interfaceCrossings());
    }

    @Test
    void acceptsAnEndpointOnAVoxelBoundary() {
        AcousticPathTracer.Result result = AcousticPathTracer.trace(
                0.5, 0.5, 0.5, 4.0, 0.5, 0.5, (x, y, z) -> AIR);

        assertFalse(result.incomplete());
    }

    @Test
    void marksUnknownSnapshotSectionsIncomplete() {
        AcousticPathTracer.Result result = AcousticPathTracer.trace(
                0.5, 0.5, 0.5, 4.5, 0.5, 0.5, (x, y, z) -> x == 2 ? UNKNOWN : AIR);

        assertTrue(result.incomplete());
        assertEquals(0, result.interfaceCrossings());
    }
}
