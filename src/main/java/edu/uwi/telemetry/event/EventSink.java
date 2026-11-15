package edu.uwi.telemetry.event;

import edu.uwi.telemetry.model.TelemetryEvent;

/**
 * Defines an interface for receiving telemetry events.
 * Provides a method for accepting events so they can be
 * handled by implementing classes.
 *
 * @author Micah Mitchell
 */

public interface EventSink {
    void accept(TelemetryEvent event);
}