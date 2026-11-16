package edu.uwi.telemetry.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the telemetry configuration settings to ensure default values
 * are applied when inputs are missing and custom settings are handled correctly.
 *
 * @author Micah Mitchell
 */

class TelemetryConfigTest {
    private static final Path HOME = Path.of("home", "student");

    @Test
    void blankSettingsFallBackToDefaults() {
        TelemetryConfig config = TelemetryConfig.resolve(" ", null, HOME);
        assertEquals(TelemetryConfig.DEFAULT_STUDENT_ID, config.studentId());
        assertEquals(HOME.resolve("BlueJTelemetry").resolve("reports"), config.reportDirectory());
        assertEquals(HOME.resolve("BlueJTelemetry").resolve("logs"), config.logDirectory());
        assertEquals(Duration.ofSeconds(1), config.samplePeriod());
        assertEquals(Duration.ofSeconds(60), config.idleThreshold());
    }

    @Test
    void customSettingsAreUsed() {
        TelemetryConfig config = TelemetryConfig.resolve(" 816012345 ", " reports-out ", HOME);
        assertEquals("816012345", config.studentId());
        assertEquals(Path.of("reports-out"), config.reportDirectory());
    }
}