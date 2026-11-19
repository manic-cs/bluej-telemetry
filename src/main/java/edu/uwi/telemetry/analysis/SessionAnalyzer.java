package edu.uwi.telemetry.analysis;

import edu.uwi.telemetry.model.EventType;
import edu.uwi.telemetry.model.ExecutionOutcome;
import edu.uwi.telemetry.model.SessionData;
import edu.uwi.telemetry.model.TelemetryEvent;
import edu.uwi.telemetry.model.TelemetryEvent.Key;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Processes telemetry events and calculates session activity statistics
 * 
 * @author Sawdah Kadir
 */

public final class SessionAnalyzer {
    /**
     * Maximum interval between activity timestamps that are classified as active
     */
    private final Duration idleThreshold;

    /**
     * Creates an analyzer with the specified idle threshold
     * @param idleThreshold maximum interval classed as active time
     */
    public SessionAnalyzer(Duration idleThreshold) {
        this.idleThreshold = Objects.requireNonNull(idleThreshold);
    }

    /**
     * Processes a telemetry session and builds its statistical summary
     * @param data session to analyze
     * @return summary containing the calculated session statistics
     */
    public SessionSummary analyze(SessionData data) {
        List<TelemetryEvent> events = new ArrayList<>(data.events());
        events.sort(Comparator.comparing(TelemetryEvent::timestamp));
        Instant start = data.start();
        Instant end = resolveEnd(data, events);

        List<Instant> activity = events.stream().filter(TelemetryEvent::isUserActivity).map(TelemetryEvent::timestamp).toList();
        ActivityTime time = ActivityTimeCalculator.calculate(start, end, activity, idleThreshold);

        SessionSummary.Builder builder = SessionSummary.builder().session(data.sessionId(), data.studentId(), start, end, data.interrupted()).times(time.active(), time.idle());

        for (TelemetryEvent event : events) {
            tally(builder, start, event);
        }

        addTimeBetweenCompilations(builder, events);
        builder.typingSeconds(countTypingSeconds(events));

        return builder.build();
    }

    /**
     * Used to update a session summary for a specific telemetry event
     */
    private interface Tally {
        void apply(SessionSummary.Builder builder, Instant sessionStart, TelemetryEvent event);
    }

    private static final Map<EventType, Tally> TALLIES = createTallies();

    private static Map<EventType, Tally> createTallies() {
        Map<EventType, Tally> t = new EnumMap<>(EventType.class);
        t.put(EventType.COMPILE_SUCCEEDED, (b, s, e) -> {
            if (isUserGenerated(e)) {
                b.compilationSucceeded();
            }
        });

        t.put(EventType.COMPILE_FAILED, (b, s, e) -> {
            if (isUserGenerated(e)) {
                b.compilationFailed();
            }
        });

        t.put(EventType.COMPILE_ERROR, (b, s, e) -> {
            if (isUserGenerated(e)) {
                b.error(e.getString(Key.CATEGORY, "Other"));
            }
        });

        t.put(EventType.COMPILE_DIAGNOSTIC, (b, s, e) -> b.errorCode(e.getString(Key.CODE, "unknown")));
        t.put(EventType.EXECUTION_FINISHED, (b, s, e) -> b.execution(outcomeOf(e)));
        t.put(EventType.EXECUTION_EXCEPTION, (b, s, e) -> b.runtimeException(e.getString(Key.EXCEPTION, "unknown")));
        t.put(EventType.EDITOR_SAMPLE, (b, s, e) -> b.editorChange());
        t.put(EventType.EDITOR_EDIT, (b, s, e) -> b.edit(e.getInt(Key.EDITS, 0), e.getInt(Key.TYPED_CHARS, 0), e.getInt(Key.BULK_CHARS, 0), e.getInt(Key.DELETED_CHARS, 0)));
        t.put(EventType.CLASS_CREATED, (b, s, e) -> {b.classCreated();b.timelineEntry(entry(s, e, "created", -1));});
        t.put(EventType.CLASS_DELETED, (b, s, e) -> {b.classDeleted();b.timelineEntry(entry(s, e, "deleted", -1));});
        t.put(EventType.CLASS_MODIFIED, (b, s, e) -> b.classModified());
        t.put(EventType.METHOD_COUNT_CHANGED, (b, s, e) -> b.timelineEntry(entry(s, e, "methods", e.getInt(Key.METHOD_COUNT, 0))));
        t.put(EventType.PROJECT_OPENED, (b, s, e) -> b.projectOpened());
        t.put(EventType.PROJECT_CREATED, (b, s, e) -> b.projectCreated());
        t.put(EventType.PROJECT_DELETED, (b, s, e) -> b.projectDeleted());

        return t;
    }

    private static void tally(SessionSummary.Builder builder, Instant start, TelemetryEvent event) {
        Tally tally = TALLIES.get(event.type());
        if (tally != null) {
            tally.apply(builder, start, event);
        }
    }

    private static boolean isUserGenerated(TelemetryEvent event) {
        return event.getBoolean(Key.USER_GENERATED, true);
    }

    /**
     * Records intervals between user compilation attempts
     */
    private static void addTimeBetweenCompilations(SessionSummary.Builder builder, List<TelemetryEvent> events) {
        Instant previous = null;
        for (TelemetryEvent event : events) {
            boolean attempt = (event.type() == EventType.COMPILE_SUCCEEDED || event.type() == EventType.COMPILE_FAILED) && isUserGenerated(event);
            if (!attempt) {
                continue;
            }

            if (previous != null) {
                builder.secondsBetweenCompilation(Duration.between(previous, event.timestamp()).getSeconds());
            }

            previous = event.timestamp();
        }
    }

    private static int countTypingSeconds(List<TelemetryEvent> events) {
        return (int) events.stream().filter(e -> e.type() == EventType.EDITOR_EDIT && e.getInt(Key.TYPED_CHARS, 0) > 0).map(e -> e.timestamp().getEpochSecond()).distinct().count();
    }

    private static ExecutionOutcome outcomeOf(TelemetryEvent event) {
        try {
            return ExecutionOutcome.valueOf(event.getString(Key.OUTCOME, ""));
        }

        catch (IllegalArgumentException ex) {
            return ExecutionOutcome.UNKNOWN;
        }
    }

    private static Instant resolveEnd(SessionData data, List<TelemetryEvent> sortedEvents) {
        Instant end = data.end();
        if (end == null) {
            end = sortedEvents.isEmpty() ? data.start() : sortedEvents.get(sortedEvents.size() - 1).timestamp();
        }

        return end.isBefore(data.start()) ? data.start() : end;
    }
    /**
     * Creates a timeline entry using a telemetry event
     * @param start session start timestamp
     * @param event containing class information
     * @param change description of the class change
     * @param methodCount recorded method count
     * @return class timeline entry
     */
    private static ClassTimelineEntry entry(Instant start, TelemetryEvent event, String change, int methodCount) {
        long offset = Math.max(0, Duration.between(start, event.timestamp()).getSeconds());
        return new ClassTimelineEntry(offset, event.getString(Key.CLASS_NAME, "?"), change, methodCount);
    }
}