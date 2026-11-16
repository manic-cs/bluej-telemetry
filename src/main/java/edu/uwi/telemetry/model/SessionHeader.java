package edu.uwi.telemetry.model;

/**
 * Represents the identifying information for a telemetry session.
 * Stores the session ID, student ID, and timestamp indicating
 * when the session started.
 *
 * @author Micah Mitchell
 */

import java.time.Instant;

public record SessionHeader(String sessionId, String studentId, Instant start) {}