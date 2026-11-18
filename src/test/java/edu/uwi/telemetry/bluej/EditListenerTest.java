package edu.uwi.telemetry.bluej;

/**
 * Unit tests for {@link EditListener} and its interaction with the {@link EditActivityTracker}.
 * <p>
 * This test suite verifies that text-editing events captured from the editor are correctly processed 
 * to record character metrics (such as typed, bulk, and deleted characters) while ensuring privacy 
 * by never storing the actual raw text strings in the generated telemetry events. It also validates 
 * robustness against null inputs and potential failures.
 * 
 * @author Rinard Guy
 */

import edu.uwi.telemetry.MutableClock;
import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.model.TelemetryEvent;
import edu.uwi.telemetry.workspace.EditActivityTracker;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EditListenerTest {
    /**
    * Tests that text replacement operations correctly record character length statistics 
    * (edits, typed chars, bulk chars, and deleted chars) and confirm that no raw text data 
    * is leaked into the telemetry payload.
    */
    @Test
    void onlyTheLengthsOfTheReplacedAndNewTextAreRecorded() {
        EditActivityTracker tracker = new EditActivityTracker(new TelemetryEventFactory(new MutableClock(Instant.parse("2026-11-01T10:00:00Z"))));
        EditListener listener = new EditListener(tracker, "Shapes", "Circle");

        listener.textReplaced(10, "old", "x", 0, 0);
        listener.textReplaced(11, "", "int radius = 5;\n", 0, 1);

        List<TelemetryEvent> events = tracker.poll();
        assertEquals(1, events.size());
        TelemetryEvent event = events.get(0);
        assertEquals("Shapes", event.getString("project", ""));
        assertEquals("Circle", event.getString("class", ""));
        assertEquals(2, event.getInt("edits", 0));
        assertEquals(1, event.getInt("typedChars", 0));
        assertEquals(16, event.getInt("bulkChars", 0));
        assertEquals(3, event.getInt("deletedChars", 0));
        assertEquals(java.util.Set.of("project", "class", "edits", "typedChars", "bulkChars", "deletedChars"), event.data().keySet(), "no text is stored in the event");
    }

    /**
    * Tests that missing text parameters (such as null values passed to text replacement) 
    * are safely tolerated and handled without disrupting tracking or bubbling exceptions to BlueJ.
    */
    @Test
    void missingTextAndFailuresNeverReachBlueJ() {
        EditActivityTracker tracker = new EditActivityTracker(new TelemetryEventFactory(new MutableClock(Instant.EPOCH)));
        new EditListener(tracker, "P", "A").textReplaced(0, null, null, 0, 0);
        assertEquals(1, tracker.poll().size());
    }
}