package edu.uwi.telemetry.workspace;

/**
 * Collects the text changes BlueJ reports for open editors (from its document listener)
 * and turns them into one EDITOR_EDIT event per editor on every sampler tick. This keeps the listener, which
 * runs on BlueJ's UI thread, down to a few additions and keeps the log small while still giving a typing
 * cadence per second. Only lengths are passed in, never the text. An insertion of ten or more characters at
 * once is counted as bulk (paste, code completion, undo or reload) and not as typing. The methods are
 * synchronized because the listener and the sampler run on different threads.
 * @author Rinard Guy
 */

import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.model.EditSummary;
import edu.uwi.telemetry.model.TelemetryEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EditActivityTracker implements PeriodicCheck {
    static final int BULK_INSERT_THRESHOLD = 10; /** An insertion this long in one change is treated as a paste, code completion, undo or reload. */

    private static final class Bucket {
        final String project;
        final String className;
        int edits;
        int typedChars;
        int bulkChars;
        int deletedChars;

        Bucket(String project, String className) {
            this.project = project;
            this.className = className;
        }
    }

    private final TelemetryEventFactory factory;
    private final Map<String, Bucket> buckets = new LinkedHashMap<>();

    public EditActivityTracker(TelemetryEventFactory factory) {
        this.factory = factory;
    }

    /** One change: {@code removedChars} were replaced by {@code addedChars} in the given class. */
    public synchronized void record(String project, String className, int removedChars, int addedChars) {
        Bucket bucket = buckets.computeIfAbsent(project + "\u0000" + className, k -> new Bucket(project, className));
        bucket.edits++;
        bucket.deletedChars += Math.max(0, removedChars);
        if (addedChars >= BULK_INSERT_THRESHOLD) {
            bucket.bulkChars += addedChars;
        }

        else {
            bucket.typedChars += Math.max(0, addedChars);
        }
    }

    @Override
    public synchronized List<TelemetryEvent> poll() {
        List<TelemetryEvent> events = new ArrayList<>();
        for (Bucket b : buckets.values()) {
            events.add(factory.editorEdit(b.project, b.className, new EditSummary(b.edits, b.typedChars, b.bulkChars, b.deletedChars)));
        }

        buckets.clear();

        return events;
    }
}