package edu.uwi.telemetry.diagnostics;

import edu.uwi.telemetry.MutableClock;
import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.model.EventType;
import edu.uwi.telemetry.model.TelemetryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests how compilation failures are handled and recorded as
 * diagnostic events. 
 *
 * @author Micah Mitchell
 */

class DiagnosticEnricherTest {
    private TelemetryEventFactory factory;
    private final List<TelemetryEvent> received = new CopyOnWriteArrayList<>();
    private final List<List<Path>> runnerCalls = new ArrayList<>();

    @BeforeEach
    void setUp() {
        factory = new TelemetryEventFactory(new MutableClock(Instant.parse("2026-11-01T10:00:00Z")));
    }

    private DiagnosticRunner fake(List<CompilerDiagnostic> result) {
        return files -> {
            runnerCalls.add(files);
            return result;
        };
    }

    @Test
    void failedUserCompileProducesOneEventPerDiagnostic() {
        DiagnosticEnricher enricher = new DiagnosticEnricher(fake(List.of(new CompilerDiagnostic("A.java", 4, "compiler.err.expected"), new CompilerDiagnostic("A.java", 9, "compiler.err.cant.resolve.location"))), factory, received::add, Runnable::run);

        enricher.compileFailed(List.of(Path.of("A.java")), true);

        assertEquals(2, received.size());
        assertEquals(EventType.COMPILE_DIAGNOSTIC, received.get(0).type());
        assertEquals("compiler.err.expected", received.get(0).getString("code", ""));
        assertEquals(9, received.get(1).getInt("line", 0));
        assertEquals("A.java", received.get(1).getString("file", ""));
    }

    @Test
    void backgroundCompilesAndEmptyFileListsAreIgnored() {
        DiagnosticEnricher enricher = new DiagnosticEnricher(fake(List.of(new CompilerDiagnostic("A.java", 1, "x"))), factory, received::add, Runnable::run);

        enricher.compileFailed(List.of(Path.of("A.java")), false);
        enricher.compileFailed(List.of(), true);
        enricher.compileFailed(null, true);

        assertTrue(received.isEmpty());
        assertTrue(runnerCalls.isEmpty());
    }

    @Test
    void runnerFailuresAreSwallowed() {
        DiagnosticEnricher enricher = new DiagnosticEnricher(files -> {
            throw new IllegalStateException("compiler exploded");
        }, factory, received::add, Runnable::run);

        enricher.compileFailed(List.of(Path.of("A.java")), true);
        assertTrue(received.isEmpty());
    }

    @Test
    void backgroundVariantDeliversEventsOnItsOwnThread() throws Exception {
        String caller = Thread.currentThread().getName();
        List<String> threads = new CopyOnWriteArrayList<>();
        DiagnosticEnricher enricher = DiagnosticEnricher.withBackgroundThread(files -> {
            threads.add(Thread.currentThread().getName());
            return List.of(new CompilerDiagnostic("A.java", 1, "compiler.err.expected"));
        }, factory, received::add);

        enricher.compileFailed(List.of(Path.of("A.java")), true);
        for (int i = 0; i < 100 && received.isEmpty(); i++) {
            Thread.sleep(20);
        }

        enricher.close();

        assertEquals(1, received.size());
        assertEquals("telemetry-javac-diagnostics", threads.get(0));
        assertTrue(!threads.get(0).equals(caller));
        enricher.compileFailed(List.of(Path.of("A.java")), true);
    }
}