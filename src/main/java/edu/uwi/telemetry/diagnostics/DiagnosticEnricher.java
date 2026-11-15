package edu.uwi.telemetry.diagnostics;

import edu.uwi.telemetry.event.EventSink;
import edu.uwi.telemetry.event.TelemetryEventFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Handles failed compilations by collecting additional details
 * about compiler errors. Runs the diagnostic process in the
 * background and records the results as telemetry events.
 * Also manages the shutdown of its background executor.
 *
 * @author Micah Mitchell
 */

public final class DiagnosticEnricher implements CompileFailureListener, AutoCloseable {
    private final DiagnosticRunner runner;
    private final TelemetryEventFactory factory;
    private final EventSink sink;
    private final Executor executor;
    private final ExecutorService ownedExecutor;

    public DiagnosticEnricher(DiagnosticRunner runner, TelemetryEventFactory factory, EventSink sink, Executor executor) {
        this(runner, factory, sink, executor, null);
    }

    private DiagnosticEnricher(DiagnosticRunner runner, TelemetryEventFactory factory, EventSink sink, Executor executor, ExecutorService owned) {
        this.runner = Objects.requireNonNull(runner);
        this.factory = Objects.requireNonNull(factory);
        this.sink = Objects.requireNonNull(sink);
        this.executor = Objects.requireNonNull(executor);
        this.ownedExecutor = owned;
    }

    public static DiagnosticEnricher withBackgroundThread(DiagnosticRunner runner, TelemetryEventFactory factory, EventSink sink) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4), runnable -> {
                    Thread thread = new Thread(runnable, "telemetry-javac-diagnostics");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.DiscardPolicy());
        return new DiagnosticEnricher(runner, factory, sink, pool, pool);
    }

    @Override
    public void compileFailed(List<Path> files, boolean userGenerated) {
        if (!userGenerated || files == null || files.isEmpty()) {
            return;
        }

        List<Path> copy = List.copyOf(files);
        try {
            executor.execute(() -> record(copy));
        }

        catch (RejectedExecutionException ex) {
        }
    }

    private void record(List<Path> files) {
        try {
            for (CompilerDiagnostic d : runner.diagnose(files)) {
                sink.accept(factory.compileDiagnostic(d.file(), d.line(), d.code()));
            }
        }

        catch (RuntimeException ex) {
        }
    }

    @Override
    public void close() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdownNow();
        }
    }
}