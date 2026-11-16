package edu.uwi.telemetry.model;

/**
 * Represents the data recorded during a telemetry session.
 * Stores session identification, start and end times, interruption
 * status, and an unmodifiable list of telemetry events.
 * Provides a method to create an updated session record with
 * a new end time and interruption status.
 *
 * @author Micah Mitchell
 */

import java.time.Instant;
import java.util.List;

public record SessionData(String sessionId, String studentId, Instant start, Instant end, boolean interrupted, List<TelemetryEvent> events) {
    public SessionData {
        events = List.copyOf(events);
    }

    public SessionData withEnd(Instant newEnd, boolean wasInterrupted) {
        return new SessionData(sessionId, studentId, start, newEnd, wasInterrupted, events);
    }
}