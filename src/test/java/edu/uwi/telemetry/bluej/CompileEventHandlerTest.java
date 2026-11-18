package edu.uwi.telemetry.bluej;

/**
 * Unit tests for {@link CompileEventHandler} and related compilation and invocation mapping logic.
 * <p>
 * This test suite verifies that BlueJ compilation lifecycle events (such as errors, warnings, starts, 
 * successes, and failures) are correctly intercepted, parsed, and translated into corresponding 
 * telemetry events. It also tests fallback mechanisms, failure isolation, and invocation outcome mapping.
 * 
 * @author Rinard Guy
 */

import bluej.extensions2.event.CompileEvent;
import bluej.extensions2.event.InvocationFinishedEvent;
import edu.uwi.telemetry.MutableClock;
import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.model.EventType;
import edu.uwi.telemetry.model.ExecutionOutcome;
import edu.uwi.telemetry.model.TelemetryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompileEventHandlerTest {
    private final List<TelemetryEvent> received = new ArrayList<>();
    private CompileEventHandler handler;

    /**
    * Sets up a fresh telemetry event handler and a mock event factory with a fixed clock 
    * before each test execution.
    */
    @BeforeEach
    void setUp() {
        TelemetryEventFactory factory = new TelemetryEventFactory(new MutableClock(Instant.parse("2026-11-01T10:00:00Z")));
        handler = new CompileEventHandler(received::add, factory);
    }

    /**
    * Helper factory method to construct mock BlueJ {@link CompileEvent} objects for testing.
    * 
    * @param type  the compilation event type (e.g., compile error, start, done)
    * @param user  flag indicating whether the compilation was user-initiated
    * @param names optional array of file names involved in the compile event
    * @return a configured {@link CompileEvent} instance
    */
    private static CompileEvent event(CompileEvent.EventType type, boolean user, String... names) {
        File[] files = new File[names.length];
        for (int i = 0; i < names.length; i++) {
            files[i] = new File("/project/" + names[i]);
        }

        return new CompileEvent(type, user, files);
    }

    /**
    * Tests that a compilation error event correctly maps the file name, line number, 
    * error category message, and user generation flag.
    */
    @Test
    void errorEventMapsLineMessageAndFile() {
        CompileEvent error = event(CompileEvent.EventType.COMPILE_ERROR_EVENT, true, "Dog.java");
        error.setErrorLineNumber(14);
        error.setErrorMessage("cannot find symbol");

        handler.compileError(error);

        TelemetryEvent mapped = received.get(0);
        assertEquals(EventType.COMPILE_ERROR, mapped.type());
        assertEquals("Dog.java", mapped.getString("file", ""));
        assertEquals(14, mapped.getInt("line", 0));
        assertEquals("Cannot find symbol", mapped.getString("category", ""));
        assertTrue(mapped.getBoolean("userGenerated", false));
    }

    /**
    * Tests that lifecycle events like start, success, failure, and warnings are mapped 
    * to their correct telemetry event types and properties.
    */
    @Test
    void successFailureStartAndWarningAreMapped() {
        handler.compileStarted(event(CompileEvent.EventType.COMPILE_START_EVENT, true, "A.java", "B.java"));
        handler.compileSucceeded(event(CompileEvent.EventType.COMPILE_DONE_EVENT, true, "A.java"));
        handler.compileFailed(event(CompileEvent.EventType.COMPILE_FAILED_EVENT, false, "A.java"));
        CompileEvent warning = event(CompileEvent.EventType.COMPILE_WARNING_EVENT, true, "A.java");
        warning.setErrorMessage("uses unchecked operations");
        handler.compileWarning(warning);

        assertEquals(List.of(EventType.COMPILE_STARTED, EventType.COMPILE_SUCCEEDED, EventType.COMPILE_FAILED, EventType.COMPILE_WARNING), received.stream().map(TelemetryEvent::type).toList());
        assertEquals(2, received.get(0).getInt("files", 0));
        assertFalse(received.get(2).getBoolean("userGenerated", true));
    }

    /**
    * Tests that compile events containing no associated files are safely handled 
    * and fall back to an "unknown" file identifier.
    */
    @Test
    void eventWithoutFilesStillWorks() {
        handler.compileError(event(CompileEvent.EventType.COMPILE_ERROR_EVENT, true));
        assertEquals("unknown", received.get(0).getString("file", ""));
    }

    /**
    * Tests that unexpected exceptions thrown by the downstream event sink do not bubble 
    * up or disrupt BlueJ's execution flow.
    */
    @Test
    void failuresInTheSinkNeverReachBlueJ() {
        CompileEventHandler throwing = new CompileEventHandler(e -> {
            throw new IllegalStateException("queue broke");
        }, new TelemetryEventFactory(new MutableClock(Instant.EPOCH)));
        throwing.compileSucceeded(event(CompileEvent.EventType.COMPILE_DONE_EVENT, true, "A.java"));
    }

    /**
    * Tests that BlueJ's invocation exit event types are accurately mapped to the 
    * corresponding internal {@link ExecutionOutcome} states.
    */
    @Test
    void invocationOutcomesAreMapped() {
        assertEquals(ExecutionOutcome.NORMAL, InvocationEventHandler.outcomeOf(InvocationFinishedEvent.EventType.NORMAL_EXIT));
        assertEquals(ExecutionOutcome.EXCEPTION, InvocationEventHandler.outcomeOf(InvocationFinishedEvent.EventType.EXCEPTION_EXIT));
        assertEquals(ExecutionOutcome.TERMINATED, InvocationEventHandler.outcomeOf(InvocationFinishedEvent.EventType.TERMINATED_EXIT));
        assertEquals(ExecutionOutcome.UNKNOWN, InvocationEventHandler.outcomeOf(InvocationFinishedEvent.EventType.UNKNOWN_EXIT));
        assertEquals(ExecutionOutcome.UNKNOWN, InvocationEventHandler.outcomeOf(null));
    }

    /**
    * Tests that a failed compilation properly triggers any registered failure listener 
    * callbacks alongside the standard telemetry event emission.
    */
    @Test
    void failedCompilationIsPassedToTheFailureListener() {
        List<List<java.nio.file.Path>> calls = new ArrayList<>();
        List<Boolean> userFlags = new ArrayList<>();
        TelemetryEventFactory factory = new TelemetryEventFactory(new MutableClock(Instant.parse("2026-11-01T10:00:00Z")));
        CompileEventHandler withListener = new CompileEventHandler(received::add, factory, (files, user) -> {
            calls.add(files);
            userFlags.add(user);
        });

        withListener.compileFailed(event(CompileEvent.EventType.COMPILE_FAILED_EVENT, true, "A.java", "B.java"));

        assertEquals(1, calls.size());
        assertEquals(2, calls.get(0).size());
        assertEquals("A.java", calls.get(0).get(0).getFileName().toString());
        assertTrue(userFlags.get(0));
        assertEquals(EventType.COMPILE_FAILED, received.get(0).type());
    }
}