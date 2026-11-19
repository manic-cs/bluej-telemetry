package edu.uwi.telemetry.analysis;

import edu.uwi.telemetry.MutableClock;
import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.model.EditSummary;
import edu.uwi.telemetry.model.EditorSample;
import edu.uwi.telemetry.model.ExecutionOutcome;
import edu.uwi.telemetry.model.SessionData;
import edu.uwi.telemetry.model.TelemetryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the analysis of telemetry events and the calculation
 * of session statistics.
 *
 * @author Sawdah Kadir
 */
class SessionAnalyzerTest {
    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    private MutableClock clock;
    private TelemetryEventFactory factory;
    private final List<TelemetryEvent> events = new ArrayList<>();
    private final SessionAnalyzer analyzer = new SessionAnalyzer(Duration.ofSeconds(60));

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        factory = new TelemetryEventFactory(clock);
    }

    private void at(int seconds, TelemetryEvent event) {
        events.add(new TelemetryEvent(T0.plusSeconds(seconds), event.type(), event.data()));
    }

    private SessionSummary analyze(int endSeconds) {
        return analyzer.analyze(new SessionData("S1", "u1", T0, T0.plusSeconds(endSeconds), false, events));
    }

    @Test
    void countsOnlyUserCompilationsAndComputesSuccessRate() {
        at(10, factory.compileFailed(1, true));
        at(20, factory.compileSucceeded(1, true));
        at(30, factory.compileSucceeded(1, true));
        at(40, factory.compileSucceeded(1, true));
        at(45, factory.compileFailed(1, false));
        at(46, factory.compileSucceeded(1, false));

        SessionSummary s = analyze(100);

        assertEquals(4, s.totalCompilations());
        assertEquals(3, s.compilationsSucceeded());
        assertEquals(1, s.compilationsFailed());
        assertEquals(0.75, s.compileSuccessRate(), 1e-9);
    }

    @Test
    void successRateIsZeroWithoutCompilations() {
        assertEquals(0.0, analyze(10).compileSuccessRate(), 1e-9);
    }

    @Test
    void measuresTimeBetweenUserCompilations() {
        at(10, factory.compileFailed(1, true));
        at(40, factory.compileFailed(1, true));
        at(100, factory.compileSucceeded(1, true));

        SessionSummary s = analyze(200);

        assertEquals(List.of(30L, 60L), s.secondsBetweenCompilations());
        assertEquals(45.0, s.averageSecondsBetweenCompilations(), 1e-9);
    }

    @Test
    void errorFrequencyIsGroupedAndSortedMostFrequentFirst() {
        at(1, factory.compileError("A.java", 1, "';' expected", true));
        at(2, factory.compileError("A.java", 2, "cannot find symbol", true));
        at(3, factory.compileError("A.java", 3, "cannot find symbol", true));
        at(4, factory.compileError("A.java", 4, "cannot find symbol", true));
        at(5, factory.compileError("A.java", 5, "missing return statement", false));

        SessionSummary s = analyze(10);

        assertEquals(List.of("Cannot find symbol", "Syntax error"), List.copyOf(s.errorFrequency().keySet()));
        assertEquals(3, s.errorFrequency().get("Cannot find symbol").intValue());
        assertEquals(4, s.totalErrors());
    }

    @Test
    void countsExecutionsByOutcome() {
        at(1, factory.executionFinished("Main", "main", ExecutionOutcome.NORMAL, false));
        at(2, factory.executionFinished("Main", "main", ExecutionOutcome.NORMAL, false));
        at(3, factory.executionFinished("Main", "main", ExecutionOutcome.EXCEPTION, false));
        at(4, factory.executionFinished("Main", "main", ExecutionOutcome.TERMINATED, false));
        at(5, factory.executionFinished("Main", "main", ExecutionOutcome.UNKNOWN, false));

        SessionSummary s = analyze(10);

        assertEquals(5, s.totalExecutions());
        assertEquals(2, s.executionsNormal());
        assertEquals(1, s.executionsWithException());
        assertEquals(1, s.executionsTerminated());
        assertEquals(1, s.executionsUnknown());
    }

    @Test
    void buildsClassTimelineWithOffsets() {
        at(30, factory.classCreated("P", "Dog"));
        at(90, factory.methodCountChanged("P", "Dog", 3));
        at(120, factory.classDeleted("P", "Dog"));
        at(150, factory.classModified("P", "Cat"));

        SessionSummary s = analyze(200);

        assertEquals(List.of(new ClassTimelineEntry(30, "Dog", "created", -1), new ClassTimelineEntry(90, "Dog", "methods", 3), new ClassTimelineEntry(120, "Dog", "deleted", -1)), s.classTimeline());
        assertEquals(1, s.classesCreated());
        assertEquals(1, s.classesDeleted());
        assertEquals(1, s.classesModified());
    }

    @Test
    void splitsActiveAndIdleTimeAndCountsEditing() {
        at(10, factory.editorSample(new EditorSample("P", "Dog", 1, 1, 5)));
        at(20, factory.editorEdit("P", "Dog", new EditSummary(5, 4, 12, 2)));

        SessionSummary s = analyze(300);

        assertEquals(Duration.ofSeconds(300), s.sessionLength());
        assertEquals(Duration.ofSeconds(20 + 60), s.activeTime());
        assertEquals(Duration.ofSeconds(220), s.idleTime());
        assertEquals(1, s.editorChanges());
        assertEquals(5, s.textEdits());
        assertEquals(4, s.charactersTyped());
        assertEquals(12, s.charactersBulkInserted());
        assertEquals(2, s.charactersDeleted());
        assertEquals(5 / (80.0 / 60.0), s.editsPerActiveMinute(), 1e-9);
        assertEquals(4 / (80.0 / 60.0), s.charactersTypedPerActiveMinute(), 1e-9);
        assertEquals(80.0 / 300.0, s.activeRatio(), 1e-9);
    }

    @Test
    void typingSpeedCountsOnlySecondsWithTypingAndIgnoresPastes() {
        at(10, factory.editorEdit("P", "A", new EditSummary(3, 6, 0, 0)));
        at(10, factory.editorEdit("P", "B", new EditSummary(1, 2, 0, 0)));
        at(11, factory.editorEdit("P", "A", new EditSummary(2, 4, 0, 0)));
        at(30, factory.editorEdit("P", "A", new EditSummary(1, 0, 200, 0)));

        SessionSummary s = analyze(60);

        assertEquals(2, s.typingSeconds());
        assertEquals(12, s.charactersTyped());
        assertEquals(200, s.charactersBulkInserted());
        assertEquals(6.0, s.typingSpeedCharsPerSecond(), 1e-9);
    }

    @Test
    void backgroundCompilesDoNotCountAsActivity() {
        at(10, factory.compileSucceeded(1, false));
        SessionSummary s = analyze(100);
        assertEquals(Duration.ofSeconds(60), s.activeTime());
    }

    @Test
    void unorderedEventsAreSortedBeforeAnalysis() {
        at(50, factory.compileSucceeded(1, true));
        at(10, factory.compileFailed(1, true));
        assertEquals(List.of(40L), analyze(100).secondsBetweenCompilations());
    }

    @Test
    void missingEndIsInferredFromLastEvent() {
        at(10, factory.compileSucceeded(1, true));
        at(75, factory.compileSucceeded(1, true));
        SessionSummary s = analyzer.analyze(new SessionData("S", "u", T0, null, true, events));
        assertEquals(T0.plusSeconds(75), s.end());
        assertEquals(Duration.ofSeconds(75), s.sessionLength());
        assertTrue(s.interrupted());
    }

    @Test
    void emptySessionIsAllZeros() {
        SessionSummary s = analyzer.analyze(new SessionData("S", "u", T0, null, false, List.of()));
        assertEquals(Duration.ZERO, s.sessionLength());
        assertEquals(0.0, s.activeRatio(), 1e-9);
        assertEquals(0.0, s.editsPerActiveMinute(), 1e-9);
        assertEquals(0.0, s.charactersTypedPerActiveMinute(), 1e-9);
        assertEquals(0.0, s.typingSpeedCharsPerSecond(), 1e-9);
        assertEquals(0.0, s.averageSecondsBetweenCompilations(), 1e-9);
        assertFalse(s.interrupted());
        assertTrue(s.errorFrequency().isEmpty());
    }

    @Test
    void countsJavacErrorCodesMostFrequentFirst() {
        at(1, factory.compileDiagnostic("A.java", 1, "compiler.err.expected"));
        at(2, factory.compileDiagnostic("A.java", 2, "compiler.err.cant.resolve.location"));
        at(3, factory.compileDiagnostic("A.java", 3, "compiler.err.cant.resolve.location"));

        SessionSummary s = analyze(10);

        assertEquals(List.of("compiler.err.cant.resolve.location", "compiler.err.expected"), List.copyOf(s.errorCodeFrequency().keySet()));
        assertEquals(2, s.errorCodeFrequency().get("compiler.err.cant.resolve.location").intValue());
    }

    @Test
    void countsProjectOpensCreationsAndDeletions() {
        at(1, factory.projectOpened("Old", 4));
        at(2, factory.projectOpened("Brand new", 0));
        at(2, factory.projectCreated("Brand new"));
        at(3, factory.projectDeleted("Old"));

        SessionSummary s = analyze(10);

        assertEquals(2, s.projectsOpened());
        assertEquals(1, s.projectsCreated());
        assertEquals(1, s.projectsDeleted());
    }

    @Test
    void diagnosticsDoNotCountAsActivity() {
        at(10, factory.compileDiagnostic("A.java", 1, "compiler.err.expected"));
        assertEquals(Duration.ofSeconds(60), analyze(100).activeTime());
    }

    @Test
    void cursorMovesAreChangesButNotTextEdits() {
        at(5, factory.editorSample(new EditorSample("P", "Dog", 2, 1, 5)));
        SessionSummary s = analyze(60);
        assertEquals(1, s.editorChanges());
        assertEquals(0, s.textEdits());
        assertEquals(0, s.charactersTyped());
    }

    @Test
    void countsUncaughtExceptionsByClassMostFrequentFirst() {
        at(1, factory.runtimeException("Main", "main", "java.lang.ArithmeticException", "/ by zero", ""));
        at(2, factory.runtimeException("Main", "main", "java.lang.NullPointerException", null, ""));
        at(3, factory.runtimeException("Main", "main", "java.lang.NullPointerException", null, ""));

        SessionSummary s = analyze(10);

        assertEquals(List.of("java.lang.NullPointerException", "java.lang.ArithmeticException"), List.copyOf(s.exceptionFrequency().keySet()));
        assertEquals(2, s.exceptionFrequency().get("java.lang.NullPointerException").intValue());
    }
}