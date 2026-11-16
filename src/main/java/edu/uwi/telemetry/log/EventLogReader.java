package edu.uwi.telemetry.log;

import edu.uwi.telemetry.json.Json;
import edu.uwi.telemetry.model.SessionData;
import edu.uwi.telemetry.model.SessionHeader;
import edu.uwi.telemetry.model.TelemetryEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads telemetry session data from a JSON Lines log file.
 * Parses session headers and telemetry events while skipping
 * blank or malformed entries. Sorts valid events chronologically
 * and reconstructs the session data. Reports an error if
 * no valid session header is found.
 *
 * @author Micah Mitchell
 */

public final class EventLogReader {
    public SessionData read(Path logFile) throws IOException {
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        SessionHeader header = null;
        List<TelemetryEvent> events = new ArrayList<>();

        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }

            Map<String, Object> map;

            try {
                map = Json.parseObject(line);
            }

            catch (IllegalArgumentException ex) {
                continue;
            }

            Object kind = map.get(EventCodec.KIND);

            try {
                if (EventCodec.KIND_SESSION.equals(kind) && header == null) {
                    header = EventCodec.decodeHeader(map);
                }

                else if (EventCodec.KIND_EVENT.equals(kind)) {
                    events.add(EventCodec.decodeEvent(map));
                }
            }

            catch (IllegalArgumentException ex) {
            }
        }

        if (header == null) {
            throw new IOException("Log has no session header: " + logFile);
        }

        events.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));

        return new SessionData(header.sessionId(), header.studentId(), header.start(), null, false, events);
    }
}