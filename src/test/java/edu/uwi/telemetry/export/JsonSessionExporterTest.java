package edu.uwi.telemetry.export;

import edu.uwi.telemetry.MutableClock;
import edu.uwi.telemetry.analysis.SessionAnalyzer;
import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.io.FileSupport;
import edu.uwi.telemetry.json.Json;
import edu.uwi.telemetry.model.EditSummary;
import edu.uwi.telemetry.model.ExecutionOutcome;
import edu.uwi.telemetry.model.SessionData;
import edu.uwi.telemetry.model.TelemetryEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests JSON session report generation, serialization and file export.
 *
 * @author Sawdah Kadir
 */
class JsonSessionExporterTest {
    static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    private Path tempDir;

    @BeforeEach
    void createTempDir() throws IOException {
        tempDir = Files.createTempDirectory("json-exporter-test");
    }

    @AfterEach
    void deleteTempDir() {
        FileSupport.deleteTree(tempDir);
    }

    static SessionReport sampleReport() {
        TelemetryEventFactory f = new TelemetryEventFactory(new MutableClock(T0));
        List<TelemetryEvent> events = new ArrayList<>();
        events.add(at(5, f.classCreated("Shapes", "Circle")));
        events.add(at(20, f.compileError("Circle.java", 7, "cannot find symbol", true)));
        events.add(at(21, f.compileFailed(1, true)));
        events.add(at(40, f.methodCountChanged("Shapes", "Circle", 2)));
        events.add(at(45, f.compileSucceeded(1, true)));
        events.add(at(50, f.executionFinished("Circle", "area", ExecutionOutcome.NORMAL, true)));
        events.add(at(70, f.editorEdit("Shapes", "Circle", new EditSummary(6, 5, 15, 1))));
        SessionData data = new SessionData("S20261101-100000-abc123", "STUDENT_ID_PLACEHOLDER", T0, T0.plusSeconds(300), false, events);

        return new SessionReport(data, new SessionAnalyzer(Duration.ofSeconds(60)).analyze(data));
    }

    private static TelemetryEvent at(int seconds, TelemetryEvent e) {
        return new TelemetryEvent(T0.plusSeconds(seconds), e.type(), e.data());
    }

    @Test
    @SuppressWarnings("unchecked")
    void documentContainsSessionMetadataAndCounts() {
        Map<String, Object> doc = JsonSessionExporter.toMap(sampleReport());

        assertEquals(1, doc.get("schemaVersion"));
        assertEquals("S20261101-100000-abc123", doc.get("sessionId"));
        assertEquals("STUDENT_ID_PLACEHOLDER", doc.get("studentId"));
        assertEquals("2026-11-01T10:00:00Z", doc.get("startTime"));
        assertEquals("2026-11-01T10:05:00Z", doc.get("endTime"));
        assertEquals(300L, doc.get("sessionLengthSeconds"));
        assertEquals(false, doc.get("interrupted"));

        Map<String, Object> compilations = (Map<String, Object>) doc.get("compilations");
        assertEquals(2, compilations.get("total"));
        assertEquals(1, compilations.get("successful"));
        assertEquals(1, compilations.get("failed"));
        assertEquals(0.5, ((Double) compilations.get("successRate")).doubleValue(), 1e-9);

        Map<String, Object> executions = (Map<String, Object>) doc.get("executions");
        assertEquals(1, executions.get("total"));
        assertEquals(Map.of("Cannot find symbol", 1), doc.get("errorFrequency"));
    }

    @Test
    void activeAndIdleTimeAddUpToSessionLength() {
        Map<String, Object> doc = JsonSessionExporter.toMap(sampleReport());
        long active = (Long) doc.get("activeSeconds");
        long idle = (Long) doc.get("idleSeconds");
        assertEquals(300L, active + idle);
    }

    @Test
    @SuppressWarnings("unchecked")
    void eventsAreListedChronologicallyWithTimestamps() {
        List<Object> events = (List<Object>) JsonSessionExporter.toMap(sampleReport()).get("events");
        assertEquals(7, events.size());
        Map<String, Object> first = (Map<String, Object>) events.get(0);
        assertEquals("2026-11-01T10:00:05Z", first.get("timestamp"));
        assertEquals("CLASS_CREATED", first.get("type"));
    }

    @Test
    void writtenFileIsValidJsonThatParsesBack() throws IOException {
        Path file = new JsonSessionExporter(tempDir.resolve("out")).export(sampleReport());

        assertEquals("session-S20261101-100000-abc123.json", file.getFileName().toString());
        Map<String, Object> parsed = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
        assertEquals("S20261101-100000-abc123", parsed.get("sessionId"));
        assertEquals(7, ((List<?>) parsed.get("events")).size());
        assertFalse(Files.exists(tempDir.resolve("out/session-S20261101-100000-abc123.json.tmp")));
    }

    @Test
    void exportingAgainOverwritesTheFile() throws IOException {
        JsonSessionExporter exporter = new JsonSessionExporter(tempDir);
        Path first = exporter.export(sampleReport());
        Path second = exporter.export(sampleReport());
        assertEquals(first, second);
        assertTrue(Files.size(second) > 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void documentIncludesErrorCodesAndProjectCounts() {
        TelemetryEventFactory f = new TelemetryEventFactory(new MutableClock(T0));
        List<TelemetryEvent> events = List.of(at(1, f.projectOpened("New", 0)), at(1, f.projectCreated("New")), at(2, f.compileDiagnostic("A.java", 3, "compiler.err.expected")), at(3, f.projectDeleted("New")), at(4, f.runtimeException("Main", "main", "java.lang.ArithmeticException", "/ by zero", "")));
        SessionData data = new SessionData("S1", "u", T0, T0.plusSeconds(10), false, events);
        SessionReport report = new SessionReport(data, new SessionAnalyzer(Duration.ofSeconds(60)).analyze(data));

        Map<String, Object> doc = JsonSessionExporter.toMap(report);

        assertEquals(Map.of("compiler.err.expected", 1), doc.get("errorCodeFrequency"));
        Map<String, Object> projects = (Map<String, Object>) doc.get("projects");
        assertEquals(1, projects.get("opened"));
        assertEquals(1, projects.get("created"));
        assertEquals(Map.of("java.lang.ArithmeticException", 1), doc.get("runtimeExceptions"));
        assertEquals(1, projects.get("deleted"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void documentIncludesTypingCadenceAndClassCounts() {
        Map<String, Object> doc = JsonSessionExporter.toMap(sampleReport());

        Map<String, Object> editing = (Map<String, Object>) doc.get("editing");
        assertEquals(6, editing.get("edits"));
        assertEquals(5, editing.get("charactersTyped"));
        assertEquals(15, editing.get("charactersBulkInserted"));
        assertEquals(1, editing.get("charactersDeleted"));
        assertEquals(1, editing.get("typingSeconds"));
        assertEquals(5.0, ((Double) editing.get("typingSpeedCharsPerSecond")).doubleValue(), 1e-9);
        Map<String, Object> classes = (Map<String, Object>) doc.get("classes");
        assertEquals(1, classes.get("created"));
        assertEquals(0, classes.get("deleted"));
    }

    @Test
    void timestampsAreTrimmedToMilliseconds() {
        Instant precise = Instant.parse("2026-11-01T10:00:00.123456789Z");
        SessionData data = new SessionData("S", "u", precise, precise.plusSeconds(5), false, List.of());
        SessionReport report = new SessionReport(data, new SessionAnalyzer(Duration.ofSeconds(60)).analyze(data));

        assertEquals("2026-11-01T10:00:00.123Z", JsonSessionExporter.toMap(report).get("startTime"));
    }
}