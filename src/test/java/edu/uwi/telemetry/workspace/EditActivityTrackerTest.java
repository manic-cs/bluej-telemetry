package edu.uwi.telemetry.workspace;

/**
 * Unit tests for {@link EditActivityTracker} and its metrics aggregation logic.
 * <p>
 * This test suite verifies that tracking edit activities correctly accumulates counts for edits, 
 * typed characters, bulk insertions, and deleted characters on a per-editor/class basis. It also 
 * validates threshold rules for bulk typing, period polling resets, and resilience against negative inputs.
 * 
 * @author Rinard Guy
 */

import edu.uwi.telemetry.MutableClock;
import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.model.EventType;
import edu.uwi.telemetry.model.TelemetryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditActivityTrackerTest {
    private EditActivityTracker tracker;

    /**
    * Initializes a fresh {@link EditActivityTracker} instance backed by a fixed-clock telemetry factory 
    * before each test execution.
    */
    @BeforeEach
    void setUp() {
        tracker = new EditActivityTracker(new TelemetryEventFactory(new MutableClock(Instant.parse("2026-11-01T10:00:00Z"))));
    }

    /**
    * Tests that polling a tracker with no recorded editing activity returns an empty event list.
    */
    @Test
    void nothingTypedMeansNoEvents() {
        assertTrue(tracker.poll().isEmpty());
    }

    /**
    * Tests that edits across multiple classes are grouped into distinct events per editor/class 
    * with correctly aggregated totals for the polling period.
    */
    @Test
    void oneEventPerEditorWithTotalsForThePeriod() {
        tracker.record("P", "Dog", 0, 1);
        tracker.record("P", "Dog", 0, 1);
        tracker.record("P", "Dog", 1, 0);
        tracker.record("P", "Cat", 0, 1);

        List<TelemetryEvent> events = tracker.poll();

        assertEquals(2, events.size());
        TelemetryEvent dog = events.get(0);
        assertEquals(EventType.EDITOR_EDIT, dog.type());
        assertEquals("Dog", dog.getString("class", ""));
        assertEquals(3, dog.getInt("edits", 0));
        assertEquals(2, dog.getInt("typedChars", 0));
        assertEquals(1, dog.getInt("deletedChars", 0));
        assertEquals(0, dog.getInt("bulkChars", 9));
        assertEquals(1, events.get(1).getInt("edits", 0));
    }

    /**
    * Tests that large text insertions exceeding or meeting the threshold are categorized 
    * as bulk modifications rather than standard user typing characters.
    */
    @Test
    void longInsertionsAreCountedAsBulkNotAsTyping() {
        tracker.record("P", "Dog", 0, EditActivityTracker.BULK_INSERT_THRESHOLD - 1);
        tracker.record("P", "Dog", 3, EditActivityTracker.BULK_INSERT_THRESHOLD);

        TelemetryEvent event = tracker.poll().get(0);

        assertEquals(EditActivityTracker.BULK_INSERT_THRESHOLD - 1, event.getInt("typedChars", 0));
        assertEquals(EditActivityTracker.BULK_INSERT_THRESHOLD, event.getInt("bulkChars", 0));
        assertEquals(3, event.getInt("deletedChars", 0));
    }

    /**
    * Tests that polling the tracker successfully flushes the current metrics buffer and 
    * initiates a fresh tracking period.
    */
    @Test
    void pollingStartsANewPeriod() {
        tracker.record("P", "Dog", 0, 1);
        assertEquals(1, tracker.poll().size());
        assertTrue(tracker.poll().isEmpty());
    }

    /**
    * Tests that malformed or negative character length arguments are safely ignored or 
    * sanitized to prevent corrupting accumulation metrics.
    */
    @Test
    void negativeLengthsAreIgnored() {
        tracker.record("P", "Dog", -5, -5);
        TelemetryEvent event = tracker.poll().get(0);
        assertEquals(0, event.getInt("typedChars", 9));
        assertEquals(0, event.getInt("deletedChars", 9));
    }
}