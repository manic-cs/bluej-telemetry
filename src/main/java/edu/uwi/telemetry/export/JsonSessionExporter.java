package edu.uwi.telemetry.export;

import edu.uwi.telemetry.analysis.SessionSummary;
import edu.uwi.telemetry.json.Json;
import edu.uwi.telemetry.model.TelemetryEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports BlueJ session reports as formatted JSON files.
 * 
 * @author Sawdah Kadir
 */
public final class JsonSessionExporter extends FileSessionExporter {
    /**
     * Version number of the exported JSON data structure
     */
    public static final int SCHEMA_VERSION = 1;

    public JsonSessionExporter(Path outputDirectory) {
        super(outputDirectory);
    }

    /**
     * Identifies the export format.
     */
    @Override
    public String formatName() {
        return "JSON";
    }

    /**
     * Returns the extension used for JSON report files.
     */
    @Override
    protected String fileExtension() {
        return "json";
    }

    /**    
     * Serializes the session report as formatted JSON and writes it
     * using UTF-8 encoding.
     */

    @Override
    protected void writeTo(SessionReport report, Path file) throws IOException {
        Files.writeString(file, Json.writePretty(toMap(report)), StandardCharsets.UTF_8);
    }

    /**
     * Converts a session report into a map representing its JSON structure.
     */
    public static Map<String, Object> toMap(SessionReport report) {
        SessionSummary s = report.summary();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("sessionId", s.sessionId());
        root.put("studentId", s.studentId());
        root.put("startTime", millis(s.start()));
        root.put("endTime", millis(s.end()));
        root.put("interrupted", s.interrupted());
        root.put("sessionLengthSeconds", s.sessionLength().getSeconds());
        root.put("activeSeconds", s.activeTime().getSeconds());
        root.put("idleSeconds", s.idleTime().getSeconds());
        root.put("compilations", compilations(s));
        root.put("executions", executions(s));
        root.put("errorFrequency", new LinkedHashMap<>(s.errorFrequency()));
        root.put("errorCodeFrequency", new LinkedHashMap<>(s.errorCodeFrequency()));
        root.put("runtimeExceptions", new LinkedHashMap<>(s.exceptionFrequency()));
        root.put("editing", editing(s));
        root.put("classes", classes(s));
        root.put("projects", projects(s));
        root.put("events", events(report));

        return root;
    }

    /**
     * Creates the JSON section containing compilation statistics.
     */
    private static Map<String, Object> compilations(SessionSummary s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total", s.totalCompilations());
        map.put("successful", s.compilationsSucceeded());
        map.put("failed", s.compilationsFailed());
        map.put("successRate", round(s.compileSuccessRate()));
        map.put("averageSecondsBetweenCompilations", round(s.averageSecondsBetweenCompilations()));

        return map;
    }

    /**
     * Creates the JSON section containing execution statistics.
     */
    private static Map<String, Object> executions(SessionSummary s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total", s.totalExecutions());
        map.put("normal", s.executionsNormal());
        map.put("withException", s.executionsWithException());
        map.put("terminated", s.executionsTerminated());

        return map;
    }

    /**
     * Creates the JSON section containing editor activity statistics.
     */
    private static Map<String, Object> editing(SessionSummary s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("cursorOrLineChanges", s.editorChanges());
        map.put("edits", s.textEdits());
        map.put("charactersTyped", s.charactersTyped());
        map.put("charactersBulkInserted", s.charactersBulkInserted());
        map.put("charactersDeleted", s.charactersDeleted());
        map.put("typingSeconds", s.typingSeconds());
        map.put("typingSpeedCharsPerSecond", round(s.typingSpeedCharsPerSecond()));
        map.put("editsPerActiveMinute", round(s.editsPerActiveMinute()));
        map.put("charactersTypedPerActiveMinute", round(s.charactersTypedPerActiveMinute()));

        return map;
    }

    /**
     * Creates the JSON section containing class change counts.
     */
    private static Map<String, Object> classes(SessionSummary s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("created", s.classesCreated());
        map.put("deleted", s.classesDeleted());
        map.put("modified", s.classesModified());

        return map;
    }

    /**
     * Creates the JSON section containing project activity counts.
     */
    private static Map<String, Object> projects(SessionSummary s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("opened", s.projectsOpened());
        map.put("created", s.projectsCreated());
        map.put("deleted", s.projectsDeleted());
        return map;
    }

    /**
     * Converts recorded telemetry events into JSON-compatible maps.
     */
    private static List<Object> events(SessionReport report) {
        List<Object> events = new ArrayList<>();
        for (TelemetryEvent event : report.data().events()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestamp", millis(event.timestamp()));
            entry.put("type", event.type().name());
            entry.put("data", event.data());
            events.add(entry);
        }

        return events;
    }

    /**
     * Truncates an instant to millisecond precision and converts it to text.
     */
    private static String millis(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MILLIS).toString();
    }

    /**
     * Rounds a decimal value to three decimal places.
     */
    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}