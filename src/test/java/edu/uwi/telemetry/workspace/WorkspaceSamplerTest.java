package edu.uwi.telemetry.workspace;

/**
 * Unit tests for {@link WorkspaceSampler} and its periodic background sampling and event-forwarding logic.
 * <p>
 * This test suite verifies that snapshot sampling correctly calculates differences, forwards events to sinks, 
 * handles missing snapshots, isolates failures gracefully, manages background timer threads safely (start/stop states), 
 * and properly executes periodic background checks.
 * 
 * @author Rinard Guy
 */

import edu.uwi.telemetry.MutableClock;
import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.model.TelemetryEvent;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceSamplerTest {
    private final TelemetryEventFactory factory = new TelemetryEventFactory(new MutableClock(Instant.parse("2026-11-01T10:00:00Z")));

    /**
    * Tests that workspace differences derived from captured snapshots are correctly forwarded 
    * to the configured event sink.
    */
    @Test
    void forwardsDifferenceEventsToTheSink() {
        List<WorkspaceSnapshot> script = new ArrayList<>(List.of(new WorkspaceSnapshot(Set.of("P"), List.of(), List.of()), new WorkspaceSnapshot(Set.of("P"), List.of(new ClassInfo("P", "A", -1)), List.of())));
        List<TelemetryEvent> received = new ArrayList<>();
        WorkspaceSampler sampler = new WorkspaceSampler(() -> script.remove(0), new WorkspaceDiffer(factory), received::add, Duration.ofSeconds(1));

        assertEquals(0, sampler.sampleOnce());
        assertEquals(1, sampler.sampleOnce());
        assertEquals(1, received.size());
    }

    /**
    * Tests that sampling ticks are safely skipped when the snapshot capture provider returns null.
    */
    @Test
    void skipsTicksWhenCaptureReturnsNull() {
        WorkspaceSampler sampler = new WorkspaceSampler(() -> null, new WorkspaceDiffer(factory), e -> {}, Duration.ofSeconds(1));
        assertEquals(0, sampler.sampleOnce());
    }

    /**
    * Tests that unexpected exceptions thrown during snapshot capture are caught and isolated, 
    * preventing crashes or failure propagation.
    */
    @Test
    void neverPropagatesFailures() {
        WorkspaceSampler sampler = new WorkspaceSampler(() -> {
            throw new IllegalStateException("BlueJ closed");
        }, new WorkspaceDiffer(factory), e -> {}, Duration.ofSeconds(1));
        assertEquals(0, sampler.sampleOnce());
    }

    /**
    * Tests that the timer successfully triggers repeated asynchronous sampling until explicitly stopped, 
    * accumulating expected events without leaking tasks post-shutdown.
    * @throws InterruptedException if thread waiting or sleeping is interrupted
    */
    @Test
    void timerSamplesRepeatedlyUntilStopped() throws InterruptedException {
        List<TelemetryEvent> received = new CopyOnWriteArrayList<>();
        CountDownLatch enoughSamples = new CountDownLatch(3);
        AtomicInteger ticks = new AtomicInteger();

        WorkspaceSampler sampler = new WorkspaceSampler(() -> {
            int n = ticks.incrementAndGet();
            return new WorkspaceSnapshot(Set.of("P"), List.of(new ClassInfo("P", "C" + n, -1)), List.of());
        }, new WorkspaceDiffer(factory), event -> {
            received.add(event);
            enoughSamples.countDown();
        }, Duration.ofMillis(10));

        sampler.start();
        sampler.start();
        assertTrue(enoughSamples.await(5, TimeUnit.SECONDS), "expected several samples but got " + received.size());

        sampler.stop();
        int after = received.size();
        assertTrue(after >= 3, "expected several samples but got " + after);

        Thread.sleep(100);
        assertEquals(after, received.size(), "no events after stop");
    }

    /**
    * Tests that invoking stop multiple times, or prior to starting the sampler, 
    * is completely safe and causes no exceptions or invalid state transitions.
    */
    @Test
    void stopIsSafeToCallTwiceAndBeforeStart() {
        WorkspaceSampler sampler = new WorkspaceSampler(() -> null, new WorkspaceDiffer(factory), e -> {}, Duration.ofMillis(10));
        sampler.stop();
        sampler.start();
        sampler.stop();
        sampler.stop();
    }

    /**
    * Tests that periodic background checks continue to execute even if main capture providers return null, 
    * and that individual check failures are successfully isolated.
    */
    @Test
    void periodicChecksRunEvenWhenCaptureIsSkippedAndFailuresAreIsolated() {
        List<TelemetryEvent> received = new ArrayList<>();
        WorkspaceSampler sampler = new WorkspaceSampler(() -> null, new WorkspaceDiffer(factory), received::add, Duration.ofSeconds(1));
        sampler.addPeriodicCheck(() -> {
            throw new IllegalStateException("broken check");
        });

        sampler.addPeriodicCheck(() -> List.of(factory.projectDeleted("Old")));

        assertEquals(1, sampler.sampleOnce());
        assertEquals("Old", received.get(0).getString("project", ""));
    }

    /**
    * Tests that stopping the sampler forces a final collection phase to capture 
    * any pending activity or state updates occurring since the last regular tick.
    */
    @Test
    void stopCollectsWhatHappenedSinceTheLastTick() {
        List<TelemetryEvent> received = new ArrayList<>();
        WorkspaceSampler sampler = new WorkspaceSampler(() -> null, new WorkspaceDiffer(factory), received::add, Duration.ofSeconds(60));
        sampler.addPeriodicCheck(() -> List.of(factory.projectDeleted("Last")));
        sampler.start();

        sampler.stop();

        assertEquals(1, received.size());
        assertEquals("Last", received.get(0).getString("project", ""));
    }
}