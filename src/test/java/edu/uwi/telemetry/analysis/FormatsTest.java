package edu.uwi.telemetry.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the formatting of durations, percentages and clock offsets.
 *
 * @author Sawdah Kadir
 */
class FormatsTest {
    @Test
    void formatsDurations() {
        assertEquals("7s", Formats.duration(7));
        assertEquals("2m 05s", Formats.duration(125));
        assertEquals("1h 02m 05s", Formats.duration(3725));
        assertEquals("0s", Formats.duration(-5));
    }

    @Test
    void formatsPercentages() {
        assertEquals("83.3%", Formats.percent(5.0 / 6.0));
        assertEquals("0.0%", Formats.percent(0));
    }

    @Test
    void formatsClockOffsets() {
        assertEquals("01:02:05", Formats.clock(3725));
        assertEquals("00:00:00", Formats.clock(-1));
    }
}