package edu.uwi.telemetry.workspace;

/**
 * Unit tests for {@link WorkspaceDiffer} and its state-differencing logic.
 * <p>
 * This test suite verifies that changes in project workspaces—such as class creation, 
 * class deletion, method count modifications, and editor activity/cursor samples—are 
 * correctly detected by comparing successive workspace snapshots against established baselines.
 * 
 * @author Rinard Guy
 */

import edu.uwi.telemetry.MutableClock;
import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.model.EditorSample;
import edu.uwi.telemetry.model.EventType;
import edu.uwi.telemetry.model.TelemetryEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceDifferTest {
    private WorkspaceDiffer differ;

    /**
    * Initializes a fresh {@link WorkspaceDiffer} instance backed by a fixed-clock telemetry factory 
    * before each test execution.
    */
    @BeforeEach
    void setUp() {
        differ = new WorkspaceDiffer(new TelemetryEventFactory(new MutableClock(Instant.parse("2026-11-01T10:00:00Z"))));
    }

    /**
    * Helper factory method to construct a mock {@link WorkspaceSnapshot} instance for testing.
    * @param projects the set of active project names
    * @param classes  the list of class metadata information
    * @param editors  the list of editor samples
    * @return a configured {@link WorkspaceSnapshot} instance
    */
    private static WorkspaceSnapshot snap(Set<String> projects, List<ClassInfo> classes, List<EditorSample> editors) {
        return new WorkspaceSnapshot(projects, classes, editors);
    }

    /**
    * Tests that the initial sighting of a project establishes a baseline snapshot 
    * and produces no diff telemetry events.
    */
    @Test
    void firstSightingOfAProjectIsOnlyABaseline() {
        List<TelemetryEvent> events = differ.diff(snap(Set.of("P"), List.of(new ClassInfo("P", "Dog", 2), new ClassInfo("P", "Cat", -1)), List.of()));
        assertTrue(events.isEmpty());
    }

    /**
    * Tests that introducing a new class to an active project generates a class creation event.
    */
    @Test
    void newClassIsReportedAsCreated() {
        differ.diff(snap(Set.of("P"), List.of(new ClassInfo("P", "Dog", -1)), List.of()));
        List<TelemetryEvent> events = differ.diff(snap(Set.of("P"), List.of(new ClassInfo("P", "Dog", -1), new ClassInfo("P", "Cat", -1)), List.of()));
        assertEquals(1, events.size());
        assertEquals(EventType.CLASS_CREATED, events.get(0).type());
        assertEquals("Cat", events.get(0).getString("class", ""));
    }

    /**
    * Tests that a class added to a previously empty project is successfully reported as created.
    */
    @Test
    void classAddedToAnEmptyProjectCountsAsCreated() {
        differ.diff(snap(Set.of("P"), List.of(), List.of()));
        List<TelemetryEvent> events = differ.diff(snap(Set.of("P"), List.of(new ClassInfo("P", "A", -1)), List.of()));
        assertEquals(EventType.CLASS_CREATED, events.get(0).type());
    }

    /**
    * Tests that removing an existing class from a project generates a class deletion event.
    */
    @Test
    void removedClassIsReportedAsDeleted() {
        differ.diff(snap(Set.of("P"), List.of(new ClassInfo("P", "Dog", 1), new ClassInfo("P", "Cat", 1)), List.of()));
        List<TelemetryEvent> events = differ.diff(snap(Set.of("P"), List.of(new ClassInfo("P", "Dog", 1)), List.of()));
        assertEquals(1, events.size());
        assertEquals(EventType.CLASS_DELETED, events.get(0).type());
        assertEquals("Cat", events.get(0).getString("class", ""));
    }

    /**
    * Tests that method count adjustments are only reported when an actual change occurs, 
    * suppressing events when counts remain constant.
    */
    @Test
    void methodCountChangesAreReportedOnlyWhenTheyChange() {
        differ.diff(snap(Set.of("P"), List.of(new ClassInfo("P", "Dog", 2)), List.of()));
        assertTrue(differ.diff(snap(Set.of("P"), List.of(new ClassInfo("P", "Dog", 2)), List.of())).isEmpty());

        List<TelemetryEvent> events = differ.diff(snap(Set.of("P"), List.of(new ClassInfo("P", "Dog", 4)), List.of()));
        assertEquals(1, events.size());
        assertEquals(EventType.METHOD_COUNT_CHANGED, events.get(0).type());
        assertEquals(4, events.get(0).getInt("methodCount", -1));
    }

    /**
    * Tests that uncompiled classes (indicated by negative method counts) do not produce method count events.
    */
    @Test
    void uncompiledClassesDoNotProduceMethodEvents() {
        differ.diff(snap(Set.of("P"), List.of(new ClassInfo("P", "Dog", 3)), List.of()));
        assertTrue(differ.diff(snap(Set.of("P"), List.of(new ClassInfo("P", "Dog", -1)), List.of())).isEmpty());
    }

    /**
    * Tests that introducing a new class containing methods correctly triggers a class creation event 
    * followed immediately by a method count change event.
    */
    @Test
    void newClassWithMethodsReportsCreationThenMethodCount() {
        differ.diff(snap(Set.of("P"), List.of(), List.of()));
        List<TelemetryEvent> events = differ.diff(snap(Set.of("P"), List.of(new ClassInfo("P", "Dog", 3)), List.of()));
        assertEquals(List.of(EventType.CLASS_CREATED, EventType.METHOD_COUNT_CHANGED), events.stream().map(TelemetryEvent::type).toList());
    }

    /**
    * Tests that closing and reopening a project resets its tracking state, 
    * treating the subsequent snapshot as a fresh baseline.
    */
    @Test
    void reopenedProjectIsABaselineAgain() {
        differ.diff(snap(Set.of("P"), List.of(new ClassInfo("P", "Dog", 1)), List.of()));
        differ.diff(snap(Set.of(), List.of(), List.of()));
        List<TelemetryEvent> events = differ.diff(snap(Set.of("P"), List.of(new ClassInfo("P", "Dog", 1), new ClassInfo("P", "Cat", 1)), List.of()));
        assertTrue(events.isEmpty());
    }

    /**
    * Tests that editor sample telemetry events are only emitted when cursor coordinates or line counts change, 
    * ignoring identical consecutive samples.
    */
    @Test
    void editorSampleOnlyWhenCursorOrLineCountChanges() {
        EditorSample a = new EditorSample("P", "Dog", 3, 4, 20);
        assertTrue(differ.diff(snap(Set.of("P"), List.of(), List.of(a))).isEmpty());
        assertTrue(differ.diff(snap(Set.of("P"), List.of(), List.of(a))).isEmpty());

        EditorSample moved = new EditorSample("P", "Dog", 3, 5, 20);
        List<TelemetryEvent> typed = differ.diff(snap(Set.of("P"), List.of(), List.of(moved)));
        assertEquals(1, typed.size());
        assertEquals(EventType.EDITOR_SAMPLE, typed.get(0).type());
        assertEquals(5, typed.get(0).getInt("column", -1));

        EditorSample longer = new EditorSample("P", "Dog", 4, 0, 21);
        assertEquals(21, differ.diff(snap(Set.of("P"), List.of(), List.of(longer))).get(0).getInt("lineCount", 0));
    }

    /**
    * Tests that closing an editor and reopening it without modifications does not register 
    * as an active edit event.
    */
    @Test
    void closingAndReopeningAnEditorIsNotAnEdit() {
        EditorSample a = new EditorSample("P", "Dog", 3, 4, 20);
        differ.diff(snap(Set.of("P"), List.of(), List.of(a)));
        differ.diff(snap(Set.of("P"), List.of(), List.of()));
        EditorSample reopened = new EditorSample("P", "Dog", 0, 0, 20);
        assertTrue(differ.diff(snap(Set.of("P"), List.of(), List.of(reopened))).isEmpty());
    }
}