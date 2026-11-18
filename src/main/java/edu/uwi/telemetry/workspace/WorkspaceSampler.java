package edu.uwi.telemetry.workspace;

import edu.uwi.telemetry.event.EventSink;
import edu.uwi.telemetry.model.TelemetryEvent;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class WorkspaceSampler {
    private static final long STOP_TIMEOUT_MILLIS = 2000;

    private final WorkspaceSource source;
    private final WorkspaceDiffer differ;
    private final EventSink sink;
    private final Duration period;
    private final List<PeriodicCheck> checks = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService executor;

    public WorkspaceSampler(WorkspaceSource source, WorkspaceDiffer differ, EventSink sink, Duration period) {
        this.source = Objects.requireNonNull(source);
        this.differ = Objects.requireNonNull(differ);
        this.sink = Objects.requireNonNull(sink);
        this.period = Objects.requireNonNull(period);
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "telemetry-sampler");
            thread.setDaemon(true);

            return thread;
        });

        long millis = Math.max(1, period.toMillis());
        executor.scheduleWithFixedDelay(this::sampleOnce, millis, millis, TimeUnit.MILLISECONDS);
    }

    public void addPeriodicCheck(PeriodicCheck check) {
        checks.add(Objects.requireNonNull(check));
    }

    public int sampleOnce() {
        return sampleWorkspace() + runChecks();
    }

    private int sampleWorkspace() {
        try {
            WorkspaceSnapshot snapshot = source.capture();
            if (snapshot == null) {
                return 0;
            }

            List<TelemetryEvent> events = differ.diff(snapshot);
            events.forEach(sink::accept);

            return events.size();
        }

        catch (RuntimeException ex) {
            return 0;
        }
    }

    private int runChecks() {
        int produced = 0;
        for (PeriodicCheck check : checks) {
            try {
                List<TelemetryEvent> events = check.poll();
                events.forEach(sink::accept);
                produced += events.size();
            }

            catch (RuntimeException ex) {
            }
        }

        return produced;
    }

    public synchronized void stop() {
        if (executor == null) {
            return;
        }

        ScheduledExecutorService stopping = executor;
        executor = null;

        stopping.shutdown();
        try {
            if (!stopping.awaitTermination(STOP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                stopping.shutdownNow();
            }
        }

        catch (InterruptedException e) {
            stopping.shutdownNow();
            Thread.currentThread().interrupt();
        }

        runChecks();
    }
}