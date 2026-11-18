package edu.uwi.telemetry.workspace;

import edu.uwi.telemetry.model.TelemetryEvent;

import java.util.List;

public interface PeriodicCheck {
    List<TelemetryEvent> poll();
}