package edu.uwi.telemetry.queue;

import edu.uwi.telemetry.model.EventPriority;
import edu.uwi.telemetry.model.TelemetryEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages a thread-safe, fixed-capacity queue of telemetry events.
 * Prioritises high-priority events by removing the oldest low-priority
 * event when the queue is full, while tracking dropped events.
 * Supports timed retrieval, immediate draining, and safe queue closure
 * using locks and condition signalling.
 *
 * @author Micah Mitchell
 */

public final class BoundedEventQueue {
    private final int capacity;
    private final ArrayDeque<TelemetryEvent> items = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private long droppedLowPriority;
    private long droppedHighPriority;
    private boolean closed;

    public BoundedEventQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }

        this.capacity = capacity;
    }

    public boolean offer(TelemetryEvent event) {
        Objects.requireNonNull(event, "event");
        lock.lock();
        try {
            if (closed) {
                return false;
            }

            if (items.size() >= capacity) {
                if (event.priority() == EventPriority.LOW) {
                    droppedLowPriority++;
                    return false;
                }

                if (!evictOldestLowPriority()) {
                    droppedHighPriority++;
                    return false;
                }
            }

            items.addLast(event);
            notEmpty.signal();

            return true;
        }

        finally {
            lock.unlock();
        }
    }

    private boolean evictOldestLowPriority() {
        Iterator<TelemetryEvent> iterator = items.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().priority() == EventPriority.LOW) {
                iterator.remove();
                droppedLowPriority++;

                return true;
            }
        }

        return false;
    }

    public List<TelemetryEvent> awaitBatch(long timeoutMillis) throws InterruptedException {
        long nanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        lock.lockInterruptibly();
        try {
            while (items.isEmpty() && !closed && nanos > 0) {
                nanos = notEmpty.awaitNanos(nanos);
            }

            return drainLocked();
        }

        finally {
            lock.unlock();
        }
    }

    public List<TelemetryEvent> drainNow() {
        lock.lock();
        try {
            return drainLocked();
        }

        finally {
            lock.unlock();
        }
    }

    private List<TelemetryEvent> drainLocked() {
        List<TelemetryEvent> batch = new ArrayList<>(items);
        items.clear();

        return batch;
    }

    public void close() {
        lock.lock();
        try {
            closed = true;
            notEmpty.signalAll();
        }

        finally {
            lock.unlock();
        }
    }

    public boolean isClosed() {
        lock.lock();
        try {
            return closed;
        }

        finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return items.size();
        }

        finally {
            lock.unlock();
        }
    }

    public long droppedLowPriority() {
        lock.lock();
        try {
            return droppedLowPriority;
        }

        finally {
            lock.unlock();
        }
    }

    public long droppedHighPriority() {
        lock.lock();
        try {
            return droppedHighPriority;
        }

        finally {
            lock.unlock();
        }
    }
}