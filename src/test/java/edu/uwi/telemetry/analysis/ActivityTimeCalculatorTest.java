package edu.uwi.telemetry.analysis;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the calculation of active and idle session time.
 * 
 * @author Sawdah Kadir
 */
class ActivityTimeCalculatorTest {
    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");
    private static final Duration THRESHOLD = Duration.ofSeconds(60);

    private static Instant at(int seconds) {
        return T0.plusSeconds(seconds);
    }

    @Test
    void continuousActivityIsAllActive() {
        ActivityTime t = ActivityTimeCalculator.calculate(T0, at(120), List.of(at(30), at(60), at(90)), THRESHOLD);
        assertEquals(Duration.ofSeconds(120), t.active());
        assertEquals(Duration.ZERO, t.idle());
    }

    @Test
    void gapLongerThanThresholdSplitsIntoActiveAndIdle() {
        ActivityTime t = ActivityTimeCalculator.calculate(T0, at(300), List.of(at(10)), THRESHOLD);
        assertEquals(Duration.ofSeconds(10 + 60), t.active());
        assertEquals(Duration.ofSeconds(230), t.idle());
    }

    @Test
    void noActivityMeansOnlyThresholdIsActive() {
        ActivityTime t = ActivityTimeCalculator.calculate(T0, at(600), List.of(), THRESHOLD);
        assertEquals(Duration.ofSeconds(60), t.active());
        assertEquals(Duration.ofSeconds(540), t.idle());
    }

    @Test
    void activeAndIdleAlwaysSumToSessionLength() {
        ActivityTime t = ActivityTimeCalculator.calculate(T0, at(1000),
                List.of(at(5), at(400), at(405), at(900)), THRESHOLD);
        assertEquals(Duration.ofSeconds(1000), t.active().plus(t.idle()));
    }

    @Test
    void ignoresActivityOutsideTheSessionAndHandlesUnorderedInput() {
        ActivityTime t = ActivityTimeCalculator.calculate(T0, at(100),
                List.of(at(90), at(-50), at(500), at(30)), THRESHOLD);
        assertEquals(Duration.ofSeconds(100), t.active());
    }

    @Test
    void endBeforeStartGivesZero() {
        ActivityTime t = ActivityTimeCalculator.calculate(at(10), T0, List.of(), THRESHOLD);
        assertEquals(Duration.ZERO, t.active());
        assertEquals(Duration.ZERO, t.idle());
    }
}