package edu.uwi.telemetry.queue;

import edu.uwi.telemetry.model.EventType;
import edu.uwi.telemetry.model.TelemetryEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests how the bounded event queue handles event priority, capacity
 * limits, and event ordering. Also checks event removal, queue closure,
 * and waiting for new events.
 *
 * @author Micah Mitchell
 */

class BoundedEventQueueTest {
    private static TelemetryEvent sample(int n) {
        return new TelemetryEvent(Instant.EPOCH.plusSeconds(n), EventType.EDITOR_SAMPLE, Map.of("n", n));
    }

    private static TelemetryEvent compile(int n) {
        return new TelemetryEvent(Instant.EPOCH.plusSeconds(n), EventType.COMPILE_FAILED, Map.of("n", n));
    }

    @Test
    void rejectsInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedEventQueue(0));
    }

    @Test
    void keepsEventsInOrder() {
        BoundedEventQueue queue = new BoundedEventQueue(5);
        queue.offer(compile(1));
        queue.offer(sample(2));
        assertEquals(List.of(compile(1), sample(2)), queue.drainNow());
        assertEquals(0, queue.size());
    }

    @Test
    void fullQueueDiscardsNewSamples() {
        BoundedEventQueue queue = new BoundedEventQueue(2);
        assertTrue(queue.offer(sample(1)));
        assertTrue(queue.offer(sample(2)));
        assertFalse(queue.offer(sample(3)));
        assertEquals(1, queue.droppedLowPriority());
        assertEquals(List.of(sample(1), sample(2)), queue.drainNow());
    }

    @Test
    void importantEventEvictsOldestSampleWhenFull() {
        BoundedEventQueue queue = new BoundedEventQueue(2);
        queue.offer(sample(1));
        queue.offer(sample(2));
        assertTrue(queue.offer(compile(3)));
        assertEquals(List.of(sample(2), compile(3)), queue.drainNow());
        assertEquals(1, queue.droppedLowPriority());
        assertEquals(0, queue.droppedHighPriority());
    }

    @Test
    void importantEventIsDroppedOnlyWhenQueueHoldsNothingElseToEvict() {
        BoundedEventQueue queue = new BoundedEventQueue(2);
        queue.offer(compile(1));
        queue.offer(compile(2));
        assertFalse(queue.offer(compile(3)));
        assertEquals(1, queue.droppedHighPriority());
        assertEquals(List.of(compile(1), compile(2)), queue.drainNow());
    }

    @Test
    void closedQueueRejectsEventsButCanBeDrained() {
        BoundedEventQueue queue = new BoundedEventQueue(3);
        queue.offer(compile(1));
        queue.close();
        assertTrue(queue.isClosed());
        assertFalse(queue.offer(compile(2)));
        assertEquals(List.of(compile(1)), queue.drainNow());
    }

    @Test
    void awaitBatchReturnsEmptyAfterTimeout() throws InterruptedException {
        BoundedEventQueue queue = new BoundedEventQueue(3);
        assertTrue(queue.awaitBatch(20).isEmpty());
    }

    @Test
    void awaitBatchWakesWhenAnEventArrives() throws Exception {
        BoundedEventQueue queue = new BoundedEventQueue(3);
        Thread producer = new Thread(() -> {
            try {
                Thread.sleep(50);
            }

            catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            queue.offer(compile(1));
        });
        producer.start();
        List<TelemetryEvent> batch = queue.awaitBatch(5000);
        producer.join();
        assertEquals(List.of(compile(1)), batch);
    }
}