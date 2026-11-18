package edu.uwi.telemetry.bluej;

/**
 * Records which exception ended a program run, including its message and the first
 * stack frames. The public Extensions API only says that a run ended with an exception, but BlueJ's own
 * event bus carries the full {@link ExecutionEvent} with its {@link ExceptionDescription} (BlueJ's
 * extension manager listens to the same bus and drops those details).
 * Only runs that end with an uncaught exception are reported; exceptions caught by the student's code are
 * not. This class relies on BlueJ internals ({@link bluej.BlueJEvent}), so a future BlueJ release could change
 * it. If that happens registration fails quietly and the rest of the plugin keeps working.
 * @author Rinard Guy
 */

import bluej.BlueJEvent;
import bluej.BlueJEventListener;
import bluej.debugger.ExceptionDescription;
import bluej.debugger.SourceLocation;
import bluej.debugmgr.ExecutionEvent;
import bluej.pkgmgr.Project;
import edu.uwi.telemetry.event.EventSink;
import edu.uwi.telemetry.event.TelemetryEventFactory;
import java.util.List;
import java.util.stream.Collectors;

public final class ExecutionResultHandler implements BlueJEventListener {
    static final int MAX_STACK_FRAMES = 8;

    private final EventSink sink;
    private final TelemetryEventFactory factory;

    public ExecutionResultHandler(EventSink sink, TelemetryEventFactory factory) {
        this.sink = sink;
        this.factory = factory;
    }

    /** Returns false if BlueJ's internal event bus is not available in this BlueJ version. */
    public boolean register() {
        try {
            BlueJEvent.addListener(this);
            return true;
        }

        catch (LinkageError | RuntimeException ex) {
            return false;
        }
    }

    public void unregister() {
        try {
            BlueJEvent.removeListener(this);
        }

        catch (LinkageError | RuntimeException ex) {
            // BlueJ is shutting down
        }
    }

    /**
    * The first few stack frames as "Class.method(File.java:12)" lines. BlueJ leaves the stack empty for
    * exceptions thrown from the code pad, so the result may be an empty string.
    */
    static String stackTraceOf(ExceptionDescription exception) {
        List<SourceLocation> stack = exception.getStack();
        if (stack == null) {
            return "";
        }

        return stack.stream().limit(MAX_STACK_FRAMES).map(f -> f.getClassName() + "." + f.getMethodName() + "(" + f.getFileName() + ":" + f.getLineNumber() + ")").collect(Collectors.joining("\n"));
    }

    @Override
    public void blueJEvent(int eventId, Object arg, Project project) {
        if (eventId != BlueJEvent.EXECUTION_RESULT || !(arg instanceof ExecutionEvent event)) {
            return;
        }

        Safe.run(() -> {
            ExceptionDescription exception = event.getException();
            if (event.getResult() == ExecutionEvent.Result.EXCEPTION_EXIT && exception != null) {
                sink.accept(factory.runtimeException(event.getClassName(), event.getMethodName(), exception.getClassName(), exception.getText(), stackTraceOf(exception)));
            }
        });
    }
}