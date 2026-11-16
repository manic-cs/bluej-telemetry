package edu.uwi.telemetry.log;

import edu.uwi.telemetry.model.EventType;
import edu.uwi.telemetry.model.SessionData;
import edu.uwi.telemetry.model.SessionHeader;
import edu.uwi.telemetry.model.TelemetryEvent;
import edu.uwi.telemetry.queue.BoundedEventQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the writing and reading of telemetry session logs.
 * Checks event storage, recovery from incomplete or invalid log entries,
 * priority-based batching, and flushing events during shutdown.
 *
 * @author Micah Mitchell
 */

class EventLogTest {
    @TempDir
    Path tempDir;

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    private static TelemetryEvent compile(int sec) {
        return new TelemetryEvent(T0.plusSeconds(sec), EventType.COMPILE_FAILED, Map.of("files", 1, "userGenerated", true));
    }

    private static TelemetryEvent sample(int sec) {
        return new TelemetryEvent(T0.plusSeconds(sec), EventType.EDITOR_SAMPLE, Map.of("class", "Dog", "line", 3, "column", sec, "lineCount", 20));
    }

    @Test
    void fileLogRoundTripsThroughReader() throws IOException {
        Path file = tempDir.resolve("logs/session.log");
        try (EventLog log = new EventLog(file)) {
            log.writeHeader(new SessionHeader("S1", "u123", T0));
            log.append(compile(5));
            log.append(sample(2));
            log.flush();
        }

        SessionData data = new EventLogReader().read(file);
        assertEquals("S1", data.sessionId());
        assertEquals("u123", data.studentId());
        assertEquals(T0, data.start());
        assertNull(data.end());
        assertEquals(2, data.events().size());
        assertEquals(EventType.EDITOR_SAMPLE, data.events().get(0).type());
        assertEquals(20, data.events().get(0).getInt("lineCount", 0));
        assertEquals(true, data.events().get(1).getBoolean("userGenerated", false));
    }

    @Test
    void readerSkipsTruncatedLastLine() throws IOException {
        Path file = tempDir.resolve("crash.log");
        try (EventLog log = new EventLog(file)) {
            log.writeHeader(new SessionHeader("S2", "u", T0));
            log.append(compile(1));
            log.flush();
        }

        Files.writeString(file, "{\"kind\":\"event\",\"timestamp\":\"2026-11", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        assertEquals(1, new EventLogReader().read(file).events().size());
    }

    @Test
    void readerRejectsLogWithoutHeader() throws IOException {
        Path file = tempDir.resolve("nohdr.log");
        Files.writeString(file, EventCodec.encodeEvent(compile(1)) + "\n");
        assertThrows(IOException.class, () -> new EventLogReader().read(file));
    }

    @Test
    void readerIgnoresUnknownEventTypes() throws IOException {
        Path file = tempDir.resolve("unknown.log");
        Files.writeString(file, EventCodec.encodeHeader(new SessionHeader("S3", "u", T0)) + "\n" + "{\"kind\":\"event\",\"timestamp\":\"2026-11-01T10:00:01Z\",\"type\":\"NOT_A_TYPE\",\"data\":{}}\n" + "\n");
        assertTrue(new EventLogReader().read(file).events().isEmpty());
    }

    @Test
    void importantEventsAreOnDiskImmediatelyButSamplesWaitForTheBatch() throws IOException {
        Path file = tempDir.resolve("writer.log");
        BoundedEventQueue queue = new BoundedEventQueue(100);
        EventLog log = new EventLog(file);
        log.writeHeader(new SessionHeader("S4", "u", T0));
        BackgroundLogWriter writer = new BackgroundLogWriter(queue, log, 3, 60_000);

        writer.write(List.of(compile(1), sample(2)));
        SessionData afterFirst = new EventLogReader().read(file);
        assertEquals(1, afterFirst.events().size(), "compile saved at once, sample still buffered");
        assertEquals(EventType.COMPILE_FAILED, afterFirst.events().get(0).type());

        writer.write(List.of(sample(3), sample(4)));
        assertEquals(4, new EventLogReader().read(file).events().size(), "third sample fills the batch");
    }

    @Test
    void stopWritesRemainingQueuedEventsAndSamples() throws IOException {
        Path file = tempDir.resolve("stop.log");
        BoundedEventQueue queue = new BoundedEventQueue(100);
        EventLog log = new EventLog(file);
        log.writeHeader(new SessionHeader("S5", "u", T0));
        BackgroundLogWriter writer = new BackgroundLogWriter(queue, log, 50, 60_000);
        writer.start();
        queue.offer(sample(1));
        queue.offer(compile(2));
        writer.stopAndAwait(5000);

        assertEquals(2, new EventLogReader().read(file).events().size());
        assertNull(writer.lastError());
    }

    @Test
    void flushNowForcesBufferedSamplesToDisk() throws IOException {
        Path file = tempDir.resolve("flush.log");
        BoundedEventQueue queue = new BoundedEventQueue(100);
        EventLog log = new EventLog(file);
        log.writeHeader(new SessionHeader("S6", "u", T0));
        BackgroundLogWriter writer = new BackgroundLogWriter(queue, log, 50, 60_000);
        queue.offer(sample(1));
        writer.flushNow();
        assertEquals(1, new EventLogReader().read(file).events().size());
        log.close();
    }
}