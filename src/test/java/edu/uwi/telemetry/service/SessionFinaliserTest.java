package edu.uwi.telemetry.service;

import edu.uwi.telemetry.analysis.SessionAnalyzer;
import edu.uwi.telemetry.export.ExportOutcome;
import edu.uwi.telemetry.export.JsonSessionExporter;
import edu.uwi.telemetry.export.SessionCompletionPublisher;
import edu.uwi.telemetry.io.FileSupport;
import edu.uwi.telemetry.json.Json;
import edu.uwi.telemetry.log.EventLog;
import edu.uwi.telemetry.log.EventLogReader;
import edu.uwi.telemetry.model.EventType;
import edu.uwi.telemetry.model.SessionHeader;
import edu.uwi.telemetry.model.TelemetryEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the completion of telemetry sessions, including session
 * duration calculations, interrupted sessions, and report generation.
 * Also checks background finalisation and error handling when
 * session logs cannot be read.
 *
 * @author Micah Mitchell
 */

class SessionFinaliserTest {
    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    private Path root;
    private SessionFinaliser finaliser;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createTempDirectory("finaliser-test");
        SessionCompletionPublisher publisher = new SessionCompletionPublisher();
        publisher.subscribe(new JsonSessionExporter(root.resolve("reports")));
        finaliser = new SessionFinaliser(new EventLogReader(), new SessionAnalyzer(Duration.ofSeconds(60)), publisher);
    }

    @AfterEach
    void cleanUp() {
        FileSupport.deleteTree(root);
    }

    private Path logWithOneCompile() throws IOException {
        Path log = root.resolve("session.log");
        try (EventLog out = new EventLog(log)) {
            out.writeHeader(new SessionHeader("S1", "u1", T0));
            out.append(new TelemetryEvent(T0.plusSeconds(30), EventType.COMPILE_SUCCEEDED, Map.of("files", 1, "userGenerated", true)));
            out.flush();
        }

        return log;
    }

    @Test
    @SuppressWarnings("unchecked")
    void finishedSessionUsesTheGivenEndTime() throws IOException {
        ExportOutcome outcome = finaliser.finalise(logWithOneCompile(), T0.plusSeconds(100), false);

        assertTrue(outcome.success(), outcome.describe());
        Map<String, Object> doc = Json.parseObject(Files.readString(outcome.files().get(0)));
        assertEquals(100L, doc.get("sessionLengthSeconds"));
        assertEquals(false, doc.get("interrupted"));
    }

    @Test
    void interruptedSessionEndsAtItsLastEvent() throws IOException {
        ExportOutcome outcome = finaliser.finalise(logWithOneCompile(), null, true);

        Map<String, Object> doc = Json.parseObject(readFirst(outcome));
        assertEquals(30L, doc.get("sessionLengthSeconds"));
        assertEquals(true, doc.get("interrupted"));
    }

    @Test
    void asTaskGivesTheSameResultAsTheDirectCall() throws IOException {
        Path log = logWithOneCompile();
        ExportOutcome outcome = finaliser.finaliseAsTask(log, T0.plusSeconds(50), false);
        assertTrue(outcome.success(), outcome.describe());
        assertEquals(50L, Json.parseObject(readFirst(outcome)).get("sessionLengthSeconds"));
    }

    @Test
    void unreadableLogIsReportedAsAProblem() {
        ExportOutcome outcome = finaliser.finalise(root.resolve("missing.log"), T0, false);
        assertFalse(outcome.success());
        assertTrue(outcome.describe().contains("Could not read the session log"));
    }

    private static String readFirst(ExportOutcome outcome) throws IOException {
        return Files.readString(outcome.files().get(0));
    }
}