package edu.uwi.telemetry.event;

import edu.uwi.telemetry.MutableClock;
import edu.uwi.telemetry.model.EditSummary;
import edu.uwi.telemetry.model.EditorSample;
import edu.uwi.telemetry.model.EventType;
import edu.uwi.telemetry.model.ExecutionOutcome;
import edu.uwi.telemetry.model.TelemetryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the creation of telemetry events for project changes, editing,
 * compilation, and program execution. Checks event timestamps, recorded
 * data, activity classification, and handling of missing or long messages.
 *
 * @author Micah Mitchell
 */

class TelemetryEventFactoryTest {
    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private TelemetryEventFactory factory;

    @BeforeEach
    void setUp() {
        factory = new TelemetryEventFactory(new MutableClock(NOW));
    }

    @Test
    void stampsEventsWithTheClockTime() {
        TelemetryEvent event = factory.projectOpened("Shapes", 3);
        assertEquals(NOW, event.timestamp());
        assertEquals(EventType.PROJECT_OPENED, event.type());
        assertEquals("Shapes", event.getString("project", ""));
        assertEquals(3, event.getInt("classCount", -1));
    }

    @Test
    void compileErrorCarriesLineMessageAndCategory() {
        TelemetryEvent event = factory.compileError("Dog.java", 12, "cannot find symbol", true);
        assertEquals(EventType.COMPILE_ERROR, event.type());
        assertEquals("Dog.java", event.getString("file", ""));
        assertEquals(12, event.getInt("line", 0));
        assertEquals("cannot find symbol", event.getString("message", ""));
        assertEquals("Cannot find symbol", event.getString("category", ""));
        assertTrue(event.getBoolean("userGenerated", false));
    }

    @Test
    void longOrMissingMessagesAreHandled() {
        TelemetryEvent longMessage = factory.compileError("A.java", 1, "x".repeat(1000), true);
        assertEquals(TelemetryEventFactory.MAX_MESSAGE_LENGTH, longMessage.getString("message", "").length());

        TelemetryEvent missing = factory.compileError(null, 1, null, false);
        assertEquals("unknown", missing.getString("file", ""));
        assertEquals("", missing.getString("message", "x"));
        assertEquals("Other", missing.getString("category", ""));
        assertFalse(missing.getBoolean("userGenerated", true));
    }

    @Test
    void executionEventRecordsOutcome() {
        TelemetryEvent event = factory.executionFinished("Main", "main", ExecutionOutcome.EXCEPTION, false);
        assertEquals("EXCEPTION", event.getString("outcome", ""));
        assertEquals("main", event.getString("method", ""));

        TelemetryEvent ctor = factory.executionFinished("Dog", null, ExecutionOutcome.NORMAL, true);
        assertEquals("<constructor>", ctor.getString("method", ""));
    }

    @Test
    void editorSampleHoldsOnlyNumbersAndNames() {
        TelemetryEvent event = factory.editorSample(new EditorSample("P", "Dog", 4, 7, 30));
        assertEquals(java.util.Set.of("project", "class", "line", "column", "lineCount"), event.data().keySet());
        assertEquals(4, event.getInt("line", -1));
        assertEquals(7, event.getInt("column", -1));
        assertEquals(30, event.getInt("lineCount", -1));
    }

    @Test
    void editorEditCarriesOnlySizes() {
        TelemetryEvent event = factory.editorEdit("P", "Dog", new EditSummary(5, 4, 12, 2));
        assertEquals(EventType.EDITOR_EDIT, event.type());
        assertEquals(java.util.Set.of("project", "class", "edits", "typedChars", "bulkChars", "deletedChars"), event.data().keySet());
        assertEquals(12, event.getInt("bulkChars", 0));
        assertTrue(event.isUserActivity());
    }

    @Test
    void userActivityIgnoresBackgroundCompiles() {
        assertFalse(factory.compileSucceeded(1, false).isUserActivity());
        assertTrue(factory.compileSucceeded(1, true).isUserActivity());
        assertTrue(factory.editorSample(new EditorSample("P", "A", 1, 1, 1)).isUserActivity());
        assertFalse(factory.projectClosed("P").isUserActivity());
    }

    @Test
    void diagnosticAndDeletionEventsCarryTheirData() {
        TelemetryEvent diagnostic = factory.compileDiagnostic("Dog.java", 8, "compiler.err.expected");
        assertEquals(EventType.COMPILE_DIAGNOSTIC, diagnostic.type());
        assertEquals("compiler.err.expected", diagnostic.getString("code", ""));
        assertEquals(8, diagnostic.getInt("line", 0));
        assertEquals("unknown", factory.compileDiagnostic(null, 1, null).getString("code", ""));

        TelemetryEvent deleted = factory.projectDeleted("Shapes");
        assertEquals(EventType.PROJECT_DELETED, deleted.type());
        assertEquals("Shapes", deleted.getString("project", ""));
    }

    @Test
    void runtimeExceptionEventKeepsClassAndShortenedMessage() {
        TelemetryEvent event = factory.runtimeException("Main", "main", "java.lang.NullPointerException", "x".repeat(900), "A.run(A.java:3)");
        assertEquals(EventType.EXECUTION_EXCEPTION, event.type());
        assertEquals("java.lang.NullPointerException", event.getString("exception", ""));
        assertEquals(TelemetryEventFactory.MAX_MESSAGE_LENGTH, event.getString("message", "").length());

        TelemetryEvent unknown = factory.runtimeException(null, null, null, null, null);
        assertEquals("unknown", unknown.getString("exception", ""));
        assertEquals("<constructor>", unknown.getString("method", ""));
        assertEquals("", unknown.getString("message", "x"));
        assertEquals("", unknown.getString("stack", "x"));
        assertEquals("A.run(A.java:3)", event.getString("stack", ""));
    }

    @Test
    void projectCreatedEventNamesTheProject() {
        TelemetryEvent event = factory.projectCreated("Shapes");
        assertEquals(EventType.PROJECT_CREATED, event.type());
        assertEquals("Shapes", event.getString("project", ""));
    }
}
