package edu.uwi.telemetry.service;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Stores the settings used to configure the telemetry system.
 * Includes the student ID, report and log directories, activity
 * timing settings, and event queue limits. Provides default
 * values and determines where project information is stored.
 *
 * @author Micah Mitchell
 */

public record TelemetryConfig(String studentId, Path reportDirectory, Path logDirectory, Duration idleThreshold, Duration samplePeriod, int queueCapacity, int sampleBatchSize) {
    public static final String DEFAULT_STUDENT_ID = "STUDENT_ID_PLACEHOLDER";
    public static final String FOLDER_NAME = "BlueJTelemetry";

    public Path knownProjectsFile() {
        return logDirectory.resolveSibling("known-projects.txt");
    }

    public static TelemetryConfig resolve(String studentId, String reportDirectory, Path userHome) {
        String id = studentId == null || studentId.isBlank() ? DEFAULT_STUDENT_ID : studentId.strip();
        Path base = userHome.resolve(FOLDER_NAME);
        Path reports = reportDirectory == null || reportDirectory.isBlank() ? base.resolve("reports") : Path.of(reportDirectory.strip());

        return new TelemetryConfig(id, reports, base.resolve("logs"), Duration.ofSeconds(60), Duration.ofSeconds(1), 1024, 20);
    }
}
