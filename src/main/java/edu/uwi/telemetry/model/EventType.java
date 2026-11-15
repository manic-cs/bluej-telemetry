package edu.uwi.telemetry.model;

/**
 * Defines the types of telemetry events recorded during a BlueJ session.
 * Associates each event type with a priority level and indicates whether
 * the event contributes to recorded user activity.
 *
 * @author Micah Mitchell
 */

public enum EventType {
    PROJECT_OPENED(EventPriority.HIGH, true),
    PROJECT_CREATED(EventPriority.HIGH, true),
    PROJECT_CLOSED(EventPriority.HIGH, false),
    PROJECT_DELETED(EventPriority.HIGH, false),
    CLASS_CREATED(EventPriority.HIGH, true),
    CLASS_DELETED(EventPriority.HIGH, true),
    CLASS_MODIFIED(EventPriority.HIGH, true),
    METHOD_COUNT_CHANGED(EventPriority.HIGH, false),
    EDITOR_SAMPLE(EventPriority.LOW, true),
    EDITOR_EDIT(EventPriority.LOW, true),
    COMPILE_STARTED(EventPriority.HIGH, true),
    COMPILE_ERROR(EventPriority.HIGH, true),
    COMPILE_WARNING(EventPriority.HIGH, true),
    COMPILE_SUCCEEDED(EventPriority.HIGH, true),
    COMPILE_FAILED(EventPriority.HIGH, true),
    COMPILE_DIAGNOSTIC(EventPriority.HIGH, false),
    EXECUTION_FINISHED(EventPriority.HIGH, true),
    EXECUTION_EXCEPTION(EventPriority.HIGH, false);

    private final EventPriority priority;
    private final boolean countsAsActivity;

    EventType(EventPriority priority, boolean countsAsActivity) {
        this.priority = priority;
        this.countsAsActivity = countsAsActivity;
    }

    public EventPriority priority() {
        return priority;
    }

    public boolean countsAsActivity() {
        return countsAsActivity;
    }
}