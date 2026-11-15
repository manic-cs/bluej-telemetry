package edu.uwi.telemetry.log;

import edu.uwi.telemetry.json.Json;
import edu.uwi.telemetry.model.EventType;
import edu.uwi.telemetry.model.SessionHeader;
import edu.uwi.telemetry.model.TelemetryEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles the encoding and decoding of telemetry events and session headers.
 * Converts session and event data into JSON strings for logging and
 * reconstructs the corresponding Java objects from parsed data.
 * Validates the decoding process by reporting malformed event
 * and session information through exceptions.
 *
 * @author Micah Mitchell
 */

public final class EventCodec {
    static final String KIND = "kind";
    static final String KIND_SESSION = "session";
    static final String KIND_EVENT = "event";

    private EventCodec() {
    }

    public static String encodeHeader(SessionHeader header) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(KIND, KIND_SESSION);
        map.put("sessionId", header.sessionId());
        map.put("studentId", header.studentId());
        map.put("start", header.start().toString());

        return Json.write(map);
    }

    public static String encodeEvent(TelemetryEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(KIND, KIND_EVENT);
        map.put("timestamp", event.timestamp().toString());
        map.put("type", event.type().name());
        map.put("data", event.data());

        return Json.write(map);
    }

    @SuppressWarnings("unchecked")
    public static TelemetryEvent decodeEvent(Map<String, Object> map) {
        try {
            Instant timestamp = Instant.parse((String) map.get("timestamp"));
            EventType type = EventType.valueOf((String) map.get("type"));
            Object data = map.get("data");
            Map<String, Object> dataMap = data instanceof Map<?, ?> ? (Map<String, Object>) data : Map.of();

            return new TelemetryEvent(timestamp, type, dataMap);
        }

        catch (RuntimeException ex) {
            throw new IllegalArgumentException("Malformed event line", ex);
        }
    }

    public static SessionHeader decodeHeader(Map<String, Object> map) {
        try {
            return new SessionHeader(
                    (String) map.get("sessionId"),
                    (String) map.get("studentId"),
                    Instant.parse((String) map.get("start")));
        }

        catch (RuntimeException ex) {
            throw new IllegalArgumentException("Malformed session header", ex);
        }
    }
}