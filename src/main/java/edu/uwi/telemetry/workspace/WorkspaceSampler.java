package edu.uwi.telemetry.workspace;

/**
 * Timer that samples the workspace (every second by default) and forwards the resulting
 * events to the {@code EventSink}. Each tick captures a snapshot from the {@code WorkspaceSource}, lets the
 * {@code WorkspaceDiffer} work out what changed and then runs every registered {@code PeriodicCheck}.
 * It uses one daemon thread and a fixed delay between ticks, never throws, and skips a tick when no snapshot
 * is available. {@code stop()} waits up to two seconds for a running tick and then runs the checks once more
 * so the last partial second of typing is not lost.
 * @author Rinard Guy
 */

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
    private static final long STOP_TIMEOUT_MILLIS = 2000; /** Maximum time stop() waits for an in-flight tick to finish. */

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

    /** Takes one sample now. Returns how many events were produced. Never throws. */
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

    /**
     * Stops sampling. When this returns, no tick is still running (unless one is stuck for
     * longer than the timeout), so no further events will reach the sink.
     */
    public synchronized void stop() {
        if (executor == null) {
            return;
        }

        ScheduledExecutorService stopping = executor;
        executor = null;

        // shutdown() cancels the periodic task but lets a tick that is already running finish cleanly.
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

        // Collect whatever happened since the last tick (for example the last second of typing)
        runChecks();
    }
}