package edu.uwi.telemetry.log;

import edu.uwi.telemetry.model.EventPriority;
import edu.uwi.telemetry.model.TelemetryEvent;
import edu.uwi.telemetry.queue.BoundedEventQueue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Manages asynchronous writing of telemetry events to a session log.
 * Processes events from a bounded queue on a background thread,
 * immediately writing high-priority events while batching low-priority
 * events based on size and time limits. Supports flushing pending events,
 * controlled shutdown, and tracking of logging errors.
 *
 * @author Micah Mitchell
 */

public final class BackgroundLogWriter {
    private final BoundedEventQueue queue;
    private final EventLog log;
    private final int sampleBatchSize;
    private final long sampleMaxAgeMillis;
    private final List<TelemetryEvent> pendingSamples = new ArrayList<>();
    private long oldestPendingSampleAt;
    private Thread thread;
    private volatile IOException lastError;

    public BackgroundLogWriter(BoundedEventQueue queue, EventLog log, int sampleBatchSize, long sampleMaxAgeMillis) {
        this.queue = Objects.requireNonNull(queue);
        this.log = Objects.requireNonNull(log);
        this.sampleBatchSize = Math.max(1, sampleBatchSize);
        this.sampleMaxAgeMillis = sampleMaxAgeMillis;
    }

    public synchronized void start() {
        if (thread != null) {
            return;
        }

        thread = new Thread(this::runLoop, "telemetry-log-writer");
        thread.setDaemon(true);
        thread.start();
    }

    private void runLoop() {
        try {
            while (true) {
                List<TelemetryEvent> batch = queue.awaitBatch(500);
                write(batch);

                if (batch.isEmpty() && queue.isClosed()) {
                    break;
                }
            }
        }

        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        finally {
            flushNow();
        }
    }

    synchronized void write(List<TelemetryEvent> batch) {
        try {
            boolean wroteImmediate = false;
            for (TelemetryEvent event : batch) {
                if (event.priority() == EventPriority.HIGH) {
                    log.append(event);
                    wroteImmediate = true;
                }

                else {
                    if (pendingSamples.isEmpty()) {
                        oldestPendingSampleAt = System.currentTimeMillis();
                    }

                    pendingSamples.add(event);
                }
            }

            if (wroteImmediate) {
                log.flush();
            }

            if (samplesDue()) {
                writePendingSamples();
            }

        }

        catch (IOException ex) {
            lastError = ex;
        }
    }

    private boolean samplesDue() {
        if (pendingSamples.isEmpty()) {
            return false;
        }

        return pendingSamples.size() >= sampleBatchSize || System.currentTimeMillis() - oldestPendingSampleAt >= sampleMaxAgeMillis;
    }

    private void writePendingSamples() throws IOException {
        for (TelemetryEvent sample : pendingSamples) {
            log.append(sample);
        }

        pendingSamples.clear();
        log.flush();
    }

    public synchronized void flushNow() {
        try {
            write(queue.drainNow());
            writePendingSamples();
            log.flush();
        }

        catch (IOException ex) {
            lastError = ex;
        }
    }

    public void stopAndAwait(long timeoutMillis) {
        queue.close();
        Thread t;
        synchronized (this) {
            t = thread;
        }

        if (t != null) {
            try {
                t.join(timeoutMillis);
            }

            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        flushNow();

        try {
            log.close();
        }

        catch (IOException ex) {
            lastError = ex;
        }
    }

    public IOException lastError() {
        return lastError;
    }
}