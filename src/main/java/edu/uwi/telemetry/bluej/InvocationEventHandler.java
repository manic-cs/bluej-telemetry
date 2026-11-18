package edu.uwi.telemetry.bluej;

/**
 * Adapter between BlueJ's {@link InvocationFinishedListener} and the plugin's {@link EventSink}.
 * Every finished method or constructor call is a program run and becomes an EXECUTION_FINISHED event with
 * the class, the method, whether a result was returned and how the run ended (normal, exception or stopped).
 * {@code outcomeOf} maps BlueJ's enum to the plugin's {@link ExecutionOutcome}. The public API does not
 * provide exception details or console output; the exception details come from {@code ExecutionResultHandler}.
 * @author Rinard Guy
 */

import bluej.extensions2.event.InvocationFinishedEvent;
import bluej.extensions2.event.InvocationFinishedListener;
import edu.uwi.telemetry.event.EventSink;
import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.model.ExecutionOutcome;

public final class InvocationEventHandler implements InvocationFinishedListener {
    private final EventSink sink;
    private final TelemetryEventFactory factory;

    public InvocationEventHandler(EventSink sink, TelemetryEventFactory factory) {
        this.sink = sink;
        this.factory = factory;
    }

    @Override
    public void invocationFinished(InvocationFinishedEvent event) {
        Safe.run(() -> sink.accept(factory.executionFinished(event.getClassName(), event.getMethodName(), outcomeOf(event.getEventType()), event.getResult() != null)));
    }

    static ExecutionOutcome outcomeOf(InvocationFinishedEvent.EventType type) {
        if (type == null) {
            return ExecutionOutcome.UNKNOWN;
        }

        return switch (type) {
            case NORMAL_EXIT -> ExecutionOutcome.NORMAL;
            case EXCEPTION_EXIT -> ExecutionOutcome.EXCEPTION;
            case TERMINATED_EXIT -> ExecutionOutcome.TERMINATED;
            default -> ExecutionOutcome.UNKNOWN;
        };
    }
}