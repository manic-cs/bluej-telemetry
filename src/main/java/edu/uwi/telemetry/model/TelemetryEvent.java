package edu.uwi.telemetry.model;

/**
 * Represents a telemetry event recorded during a BlueJ session.
 * Stores the event timestamp, type, and associated data while
 * providing methods to retrieve event properties and priority.
 * Supports identifying user activity and comparing events
 * based on their recorded information.
 *
 * @author Micah Mitchell
 */

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class TelemetryEvent {
    public static final class Key {
        public static final String PROJECT = "project";
        public static final String CLASS_NAME = "class";
        public static final String CLASS_COUNT = "classCount";
        public static final String METHOD_COUNT = "methodCount";
        public static final String LINE = "line";
        public static final String COLUMN = "column";
        public static final String LINE_COUNT = "lineCount";
        public static final String FILE = "file";
        public static final String FILES = "files";
        public static final String MESSAGE = "message";
        public static final String CATEGORY = "category";
        public static final String CODE = "code";
        public static final String USER_GENERATED = "userGenerated";
        public static final String METHOD = "method";
        public static final String OUTCOME = "outcome";
        public static final String HAS_RESULT = "hasResult";
        public static final String EDITS = "edits";
        public static final String TYPED_CHARS = "typedChars";
        public static final String BULK_CHARS = "bulkChars";
        public static final String DELETED_CHARS = "deletedChars";
        public static final String STACK = "stack";
        public static final String EXCEPTION = "exception";

        private Key() {
        }
    }

    private final Instant timestamp;
    private final EventType type;
    private final Map<String, Object> data;

    public TelemetryEvent(Instant timestamp, EventType type, Map<String, Object> data) {
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.type = Objects.requireNonNull(type, "type");
        this.data = data == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public Instant timestamp() {
        return timestamp;
    }
    public EventType type() {
        return type;
    }
    public Map<String, Object> data() {
        return data;
    }
    public EventPriority priority() {
        return type.priority();
    }

    public String getString(String key, String defaultValue) {
        Object value = data.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    public int getInt(String key, int defaultValue) {
        Object value = data.get(key);
        return value instanceof Number n ? n.intValue() : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = data.get(key);
        return value instanceof Boolean b ? b : defaultValue;
    }

    public boolean isUserActivity() {
        return type.countsAsActivity() && getBoolean(Key.USER_GENERATED, true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TelemetryEvent other)) {
            return false;
        }
        return timestamp.equals(other.timestamp) && type == other.type && data.equals(other.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, type, data);
    }

    @Override
    public String toString() {
        return timestamp + " " + type + " " + data;
    }
}