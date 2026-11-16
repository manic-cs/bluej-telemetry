package edu.uwi.telemetry.service;

import edu.uwi.telemetry.MutableClock;
import edu.uwi.telemetry.analysis.SessionAnalyzer;
import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.export.ExportOutcome;
import edu.uwi.telemetry.io.FileSupport;
import edu.uwi.telemetry.export.JsonSessionExporter;
import edu.uwi.telemetry.export.SessionCompletionPublisher;
import edu.uwi.telemetry.export.SessionExporter;
import edu.uwi.telemetry.export.SessionReport;
import edu.uwi.telemetry.json.Json;
import edu.uwi.telemetry.log.EventLog;
import edu.uwi.telemetry.log.EventLogReader;
import edu.uwi.telemetry.model.ExecutionOutcome;
import edu.uwi.telemetry.model.SessionHeader;
import edu.uwi.telemetry.workspace.ClassInfo;
import edu.uwi.telemetry.model.EditorSample;
import edu.uwi.telemetry.workspace.WorkspaceSnapshot;
import edu.uwi.telemetry.workspace.WorkspaceSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the telemetry service's session management, event logging,
 * and report generation. Also checks manual exports, error handling,
 * workspace monitoring, and recovery of interrupted sessions.
 *
 * @author Micah Mitchell
 */

class TelemetryServiceTest {
    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    private Path tempDir;

    @BeforeEach
    void createTempDir() throws IOException {
        tempDir = Files.createTempDirectory("telemetry-service-test");
    }

    @AfterEach
    void deleteTempDir() {
        FileSupport.deleteTree(tempDir);
    }

    private final WorkspaceSource idleWorkspace = WorkspaceSnapshot::empty;

    private TelemetryConfig config() {
        return new TelemetryConfig("816000001", tempDir.resolve("reports"), tempDir.resolve("logs"), Duration.ofSeconds(60), Duration.ofSeconds(1), 50, 5);
    }

    private SessionCompletionPublisher jsonOnly() {
        SessionCompletionPublisher publisher = new SessionCompletionPublisher();
        publisher.subscribe(new JsonSessionExporter(tempDir.resolve("reports")));

        return publisher;
    }

    private SessionFinaliser finaliser() {
        return new SessionFinaliser(new EventLogReader(), new SessionAnalyzer(Duration.ofSeconds(60)), jsonOnly());
    }

    private long countFiles(Path dir, String glob) throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }

        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().matches(glob)).count();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void fullSessionRecordsEventsAndWritesReportsAtTheEnd() throws IOException {
        MutableClock clock = new MutableClock(T0);
        TelemetryService service = new TelemetryService(config(), idleWorkspace, clock, jsonOnly());

        assertTrue(service.start());
        assertTrue(service.isActive());
        TelemetryEventFactory f = service.events();

        service.accept(f.compileError("Dog.java", 3, "cannot find symbol", true));
        service.accept(f.compileFailed(1, true));
        clock.advance(Duration.ofSeconds(30));
        service.accept(f.compileSucceeded(1, true));
        clock.advance(Duration.ofSeconds(10));
        service.accept(f.executionFinished("Dog", "bark", ExecutionOutcome.NORMAL, false));
        clock.advance(Duration.ofSeconds(20));

        ExportOutcome outcome = service.end();

        assertTrue(outcome.success(), outcome.describe());
        assertFalse(service.isActive());
        Path json = outcome.files().get(0);
        Map<String, Object> doc = Json.parseObject(Files.readString(json, StandardCharsets.UTF_8));
        assertEquals("816000001", doc.get("studentId"));
        assertEquals(60L, doc.get("sessionLengthSeconds"));
        Map<String, Object> compilations = (Map<String, Object>) doc.get("compilations");
        assertEquals(2L, compilations.get("total"));
        assertEquals(1L, compilations.get("successful"));
        assertEquals(1L, ((Map<String, Object>) doc.get("executions")).get("total"));
        assertEquals(0, countFiles(tempDir.resolve("logs"), ".*\\.log"), "temporary log removed after export");
    }

    @Test
    void endingTwiceReturnsTheSameOutcome() {
        TelemetryService service = new TelemetryService(config(), idleWorkspace, new MutableClock(T0), jsonOnly());
        service.start();
        ExportOutcome first = service.end();
        assertEquals(first, service.end());
    }

    @Test
    void manualExportKeepsTheSessionRunning() throws IOException {
        MutableClock clock = new MutableClock(T0);
        TelemetryService service = new TelemetryService(config(), idleWorkspace, clock, jsonOnly());
        service.start();
        service.accept(service.events().compileSucceeded(1, true));
        clock.advance(Duration.ofSeconds(5));

        ExportOutcome manual = service.exportNow();

        assertTrue(manual.success(), manual.describe());
        assertTrue(service.isActive());
        assertEquals(1, countFiles(tempDir.resolve("logs"), ".*\\.log"), "log kept while the session runs");

        service.accept(service.events().compileSucceeded(1, true));
        ExportOutcome last = service.end();
        assertEquals(manual.files(), last.files(), "same session id, so the file is overwritten");
        assertEquals(1, countFiles(tempDir.resolve("reports"), ".*\\.json"));
    }

    @Test
    void exportBeforeStartExplainsTheProblem() {
        TelemetryService service = new TelemetryService(config(), idleWorkspace, new MutableClock(T0), jsonOnly());
        ExportOutcome outcome = service.exportNow();
        assertFalse(outcome.success());
        assertTrue(outcome.describe().contains("No telemetry session"));
        assertFalse(service.end().success());
    }

    @Test
    void failedExportKeepsTheLogForAnotherTry() throws IOException {
        SessionCompletionPublisher failing = new SessionCompletionPublisher();
        failing.subscribe(new SessionExporter() {
            @Override
            public String formatName() {
                return "PDF";
            }

            @Override
            public Path export(SessionReport report) throws IOException {
                throw new IOException("printer on fire");
            }
        });

        TelemetryService service = new TelemetryService(config(), idleWorkspace, new MutableClock(T0), failing);
        service.start();

        ExportOutcome outcome = service.end();

        assertFalse(outcome.success());
        assertTrue(outcome.describe().contains("printer on fire"));
        assertEquals(1, countFiles(tempDir.resolve("logs"), ".*\\.log"));
    }

    @Test
    void workspaceSamplerEventsReachTheLog() throws Exception {
        List<WorkspaceSnapshot> script = new java.util.concurrent.CopyOnWriteArrayList<>(List.of(new WorkspaceSnapshot(Set.of("P"), List.of(new ClassInfo("P", "A", -1)), List.of(new EditorSample("P", "A", 1, 1, 5))), new WorkspaceSnapshot(Set.of("P"), List.of(new ClassInfo("P", "A", -1), new ClassInfo("P", "B", -1)), List.of(new EditorSample("P", "A", 1, 2, 5)))));
        int[] index = {0};
        WorkspaceSource source = () -> script.get(Math.min(index[0]++, script.size() - 1));
        TelemetryConfig fast = new TelemetryConfig("u", tempDir.resolve("reports"), tempDir.resolve("logs"), Duration.ofSeconds(60), Duration.ofMillis(20), 50, 5);
        TelemetryService service = new TelemetryService(fast, source, Clock.systemUTC(), jsonOnly());
        service.start();
        Thread.sleep(400);

        ExportOutcome outcome = service.end();

        String json = Files.readString(outcome.files().get(0));
        assertTrue(json.contains("CLASS_CREATED"), json);
        assertTrue(json.contains("EDITOR_SAMPLE"), json);
    }

    @Test
    void unwritableLogDirectoryDisablesTelemetryWithoutThrowing() throws IOException {
        Path blocker = Files.writeString(tempDir.resolve("not-a-dir"), "x");
        TelemetryConfig broken = new TelemetryConfig("u", tempDir.resolve("reports"), blocker.resolve("logs"), Duration.ofSeconds(60), Duration.ofSeconds(1), 10, 5);
        TelemetryService service = new TelemetryService(broken, idleWorkspace, new MutableClock(T0), jsonOnly());

        assertFalse(service.start());
        assertFalse(service.isActive());
        service.accept(service.events().compileFailed(1, true));
    }

    @Test
    void interruptedSessionsAreRecoveredAtNextLaunch() throws Exception {
        Path logs = tempDir.resolve("logs");
        Path crashed = logs.resolve("session-SCRASHED.log");
        TelemetryEventFactory f = new TelemetryEventFactory(new MutableClock(T0));
        try (EventLog log = new EventLog(crashed)) {
            log.writeHeader(new SessionHeader("SCRASHED", "u9", T0));
            log.append(new edu.uwi.telemetry.model.TelemetryEvent(T0.plusSeconds(40), edu.uwi.telemetry.model.EventType.COMPILE_FAILED, Map.of("files", 1, "userGenerated", true)));
            log.flush();
        }

        Files.writeString(crashed, "{\"kind\":\"event\",\"timest", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);

        TelemetryService service = new TelemetryService(config(), idleWorkspace, new MutableClock(T0), jsonOnly());
        service.start();
        Path recovered = tempDir.resolve("reports/session-SCRASHED.json");

        for (int i = 0; i < 100 && !Files.exists(recovered); i++) {
            Thread.sleep(50);
        }

        service.end();

        assertTrue(Files.exists(recovered), "recovered report written");
        Map<String, Object> doc = Json.parseObject(Files.readString(recovered));
        assertEquals(true, doc.get("interrupted"));
        assertEquals("u9", doc.get("studentId"));
        assertEquals(40L, doc.get("sessionLengthSeconds"));
        assertFalse(Files.exists(crashed), "recovered log removed");
    }

    @Test
    void recoverySkipsUnreadableLogsAndCurrentLog() throws IOException {
        Path logs = tempDir.resolve("logs");
        Files.createDirectories(logs);
        Path garbage = Files.writeString(logs.resolve("session-garbage.log"), "not json at all\n");
        Path current = Files.writeString(logs.resolve("session-current.log"), "");

        SessionRecovery recovery = new SessionRecovery(logs, current, finaliser());

        assertEquals(0, recovery.recoverAll());
        assertTrue(Files.exists(garbage));
        assertTrue(Files.exists(current));
        assertEquals(0, new SessionRecovery(tempDir.resolve("missing"), null, finaliser()).recoverAll());
    }

    @Test
    void sessionIdsAreUniqueAndTimestamped() {
        MutableClock clock = new MutableClock(T0);
        String a = TelemetryService.newSessionId(clock);
        String b = TelemetryService.newSessionId(clock);
        assertTrue(a.startsWith("S20261101-100000-"), a);
        assertFalse(a.equals(b));
    }

    @Test
    void defaultPublisherWritesJsonAndPdf() {
        assertEquals(2, TelemetryService.defaultPublisher(tempDir).subscriberCount());
    }
}