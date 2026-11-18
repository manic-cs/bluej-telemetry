package edu.uwi.telemetry.bluej;

/**
 * Unit tests for {@link ExecutionResultHandler} and its ability to process execution outcomes 
 * and capture uncaught runtime exceptions from BlueJ.
 * <p>
 * This test suite verifies that execution events resulting in exceptions are correctly intercepted, 
 * translated into telemetry events with detailed stack traces, and filtered when dealing with normal exits, 
 * missing descriptions, or unrelated event types.
 * 
 * @author Rinard Guy
 */

import bluej.BlueJEvent;
import bluej.debugger.ExceptionDescription;
import bluej.debugger.SourceLocation;
import bluej.debugmgr.ExecutionEvent;
import edu.uwi.telemetry.MutableClock;
import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.model.EventType;
import edu.uwi.telemetry.model.TelemetryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionResultHandlerTest {
    private final List<TelemetryEvent> received = new ArrayList<>();
    private ExecutionResultHandler handler;

    /**
    * Sets up a fresh execution result handler and a mock event factory with a fixed clock 
    * before each test execution.
    */
    @BeforeEach
    void setUp() {
        handler = new ExecutionResultHandler(received::add, new TelemetryEventFactory(new MutableClock(Instant.parse("2026-11-01T10:00:00Z"))));
    }

    /**
     * Helper factory method to construct mock BlueJ {@link ExecutionEvent} instances for testing.
     * @param result    the outcome/result state of the execution run
     * @param exception the description of any exception thrown during execution
     * @return a configured {@link ExecutionEvent} instance
     */
    private static ExecutionEvent runEndingWith(ExecutionEvent.Result result, ExceptionDescription exception) {
        ExecutionEvent event = new ExecutionEvent(null, "Calculator", null);
        event.setMethodName("divide");
        event.setResult(result);
        event.setException(exception);

        return event;
    }

    /**
    * Tests that an uncaught exception during execution correctly records the exception type, 
    * error message, target class, method name, and formatted stack trace string.
    */
    @Test
    void uncaughtExceptionIsRecordedWithItsClassAndMessage() {
        ExecutionEvent event = runEndingWith(ExecutionEvent.Result.EXCEPTION_EXIT, new ExceptionDescription("java.lang.ArithmeticException", "/ by zero", List.of(new SourceLocation("Calculator", "Calculator.java", "divide", 12), new SourceLocation("Main", "Main.java", "main", 5))));

        handler.blueJEvent(BlueJEvent.EXECUTION_RESULT, event, null);

        assertEquals(1, received.size());
        TelemetryEvent recorded = received.get(0);
        assertEquals(EventType.EXECUTION_EXCEPTION, recorded.type());
        assertEquals("java.lang.ArithmeticException", recorded.getString("exception", ""));
        assertEquals("/ by zero", recorded.getString("message", ""));
        assertEquals("Calculator", recorded.getString("class", ""));
        assertEquals("divide", recorded.getString("method", ""));
        assertEquals("Calculator.divide(Calculator.java:12)\nMain.main(Main.java:5)", recorded.getString("stack", ""));
    }

    /**
    * Tests that stack traces are properly capped to the maximum allowed frame limit 
    * and correctly handle empty or null stack collections without failing.
    */
    @Test
    void stackTraceIsShortenedAndMayBeEmpty() {
        List<SourceLocation> deep = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            deep.add(new SourceLocation("C" + i, "C.java", "m", i));
        }

        String stack = ExecutionResultHandler.stackTraceOf(new ExceptionDescription("E", "x", deep));
        assertEquals(ExecutionResultHandler.MAX_STACK_FRAMES, stack.split("\n").length);

        assertEquals("", ExecutionResultHandler.stackTraceOf(new ExceptionDescription("E", "x", List.of())));
        assertEquals("", ExecutionResultHandler.stackTraceOf(new ExceptionDescription("E", "x", null)));
    }

    /**
    * Tests that normal execution completions and terminated runs do not trigger telemetry reporting.
    */
    @Test
    void normalAndTerminatedRunsAreIgnored() {
        handler.blueJEvent(BlueJEvent.EXECUTION_RESULT, runEndingWith(ExecutionEvent.Result.NORMAL_EXIT, null), null);
        handler.blueJEvent(BlueJEvent.EXECUTION_RESULT, runEndingWith(ExecutionEvent.Result.TERMINATED_EXIT, null), null);
        assertTrue(received.isEmpty());
    }

    /**
    * Tests that execution exits marked with an exception status but lacking an actual 
    * exception description are safely ignored.
    */
    @Test
    void exceptionExitWithoutDescriptionIsIgnored() {
        handler.blueJEvent(BlueJEvent.EXECUTION_RESULT, runEndingWith(ExecutionEvent.Result.EXCEPTION_EXIT, null), null);
        assertTrue(received.isEmpty());
    }

    /**
    * Tests that irrelevant BlueJ events or malformed arguments are filtered out and ignored.
    */
    @Test
    void otherBlueJEventsAndOtherArgumentsAreIgnored() {
        ExecutionEvent event = runEndingWith(ExecutionEvent.Result.EXCEPTION_EXIT, new ExceptionDescription("java.lang.Error", "x", List.of()));

        handler.blueJEvent(BlueJEvent.METHOD_CALL, event, null);
        handler.blueJEvent(BlueJEvent.EXECUTION_RESULT, "not an execution event", null);
        handler.blueJEvent(BlueJEvent.EXECUTION_RESULT, null, null);

        assertTrue(received.isEmpty());
    }

    /**
    * Tests that unexpected exceptions thrown by the event sink during handling do not bubble 
    * up or crash BlueJ's internal execution thread.
    */
    @Test
    void failingSinkNeverReachesBlueJ() {
        ExecutionResultHandler throwing = new ExecutionResultHandler(e -> {
            throw new IllegalStateException("queue broke");
        }, new TelemetryEventFactory(new MutableClock(Instant.EPOCH)));

        throwing.blueJEvent(BlueJEvent.EXECUTION_RESULT, runEndingWith(ExecutionEvent.Result.EXCEPTION_EXIT, new ExceptionDescription("java.lang.Error", "x", List.of())), null);
    }
}