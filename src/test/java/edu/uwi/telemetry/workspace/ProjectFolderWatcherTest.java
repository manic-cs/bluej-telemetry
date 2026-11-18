package edu.uwi.telemetry.workspace;

/**
 * Unit tests for {@link ProjectFolderWatcher} and its file-system monitoring logic.
 * <p>
 * This test suite verifies that the watcher accurately detects project creation (via marker file inspection 
 * within a specific time window) and project deletion (handling both live deletions and cross-session checks 
 * using a local registry file). It also validates throttling mechanisms, parent-folder safety guards (preventing 
 * false deletions from unplugged drives), and resilience against malformed registries.
 * 
 * @author Rinard Guy
 */

import edu.uwi.telemetry.MutableClock;
import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.io.FileSupport;
import edu.uwi.telemetry.model.EventType;
import edu.uwi.telemetry.model.TelemetryEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectFolderWatcherTest {
    private Path root;
    private MutableClock clock;
    private TelemetryEventFactory factory;
    private Path registry;

    /**
     * Prepares a fresh temporary directory, a mutable mock clock, an event factory, 
     * and a registry file path before each test execution.
     * @throws IOException if temporary directory creation fails
     */
    @BeforeEach
    void setUp() throws IOException {
        root = Files.createTempDirectory("folder-watcher-test");
        clock = new MutableClock(Instant.now());
        factory = new TelemetryEventFactory(clock);
        registry = root.resolve("known-projects.txt");
    }

    /**
    * Cleans up and deletes the temporary test directory tree after each test execution.
    */
    @AfterEach
    void cleanUp() {
        FileSupport.deleteTree(root);
    }

    /**
    * Helper factory method to initialize a new {@link ProjectFolderWatcher} instance 
    * configured with standard test parameters.
    * @return a configured {@link ProjectFolderWatcher} instance
    */
    private ProjectFolderWatcher newWatcher() {
        return new ProjectFolderWatcher(factory, clock, Duration.ofSeconds(5), registry);
    }

    /**
    * Helper utility to create a physical project folder within the temporary root directory, 
    * optionally including a creation marker file.
    * @param name           the name of the project folder
    * @param withMarker     whether to generate the project marker file inside the folder
    * @return the path of the created project folder
    * @throws IOException if folder or file creation fails
    */
    private Path project(String name, boolean withMarker) throws IOException {
        Path folder = Files.createDirectory(root.resolve(name));
        if (withMarker) {
            Files.writeString(folder.resolve(ProjectFolderWatcher.PROJECT_MARKER_FILE), "");
        }

        return folder;
    }

    /**
    * Tests that tracking pre-existing folders that lack a recent creation marker 
    * produces no creation telemetry events.
    * @throws IOException if file system operations fail
    */
    @Test
    void existingFoldersProduceNothing() throws IOException {
        ProjectFolderWatcher watcher = newWatcher();
        watcher.track("Shapes", project("Shapes", false));
        assertTrue(watcher.poll().isEmpty());
    }

    /**
    * Tests that a project whose marker file was recently created is correctly reported 
    * as a new project creation event exactly once.
    * @throws IOException if file system operations fail
    */
    @Test
    void projectWhoseMarkerFileWasJustCreatedIsReportedAsCreated() throws IOException {
        ProjectFolderWatcher watcher = newWatcher();

        watcher.track("Fresh", project("Fresh", true));

        List<TelemetryEvent> events = watcher.poll();
        assertEquals(1, events.size());
        assertEquals(EventType.PROJECT_CREATED, events.get(0).type());
        assertEquals("Fresh", events.get(0).getString("project", ""));
        assertTrue(watcher.poll().isEmpty(), "reported only once");
    }

    /**
    * Tests that projects opened long after their marker file creation timestamp 
    * are not classified as newly created projects.
    * @throws IOException if file system operations fail
    */
    @Test
    void projectOpenedLongAfterItsCreationIsNotNew() throws IOException {
        ProjectFolderWatcher watcher = newWatcher();
        Path old = project("Old", true);

        clock.advance(ProjectFolderWatcher.CREATION_WINDOW.plusSeconds(60));
        watcher.track("Old", old);

        assertTrue(watcher.poll().isEmpty());
    }

    /**
    * Tests that deleting a tracked project folder generates a deletion telemetry event 
    * exactly once, clears tracking, and updates internal state.
    * @throws IOException if file system operations fail
    */
    @Test
    void deletedFolderIsReportedOnceWithTheProjectName() throws IOException {
        ProjectFolderWatcher watcher = newWatcher();
        Path folder = project("Shapes", false);
        watcher.track("Shapes", folder);
        Files.delete(folder);

        List<TelemetryEvent> events = watcher.poll();

        assertEquals(1, events.size());
        assertEquals(EventType.PROJECT_DELETED, events.get(0).type());
        assertEquals("Shapes", events.get(0).getString("project", ""));
        clock.advance(Duration.ofSeconds(10));
        assertTrue(watcher.poll().isEmpty(), "reported only once");
        assertEquals(0, watcher.trackedCount());
    }

    /**
    * Tests that file system checks are properly throttled based on the minimum configured interval.
    * @throws IOException if file system operations fail
    */
    @Test
    void checksAreThrottledToTheMinimumInterval() throws IOException {
        ProjectFolderWatcher watcher = newWatcher();
        Path folder = project("P", false);
        watcher.track("P", folder);
        assertTrue(watcher.poll().isEmpty());
        Files.delete(folder);

        clock.advance(Duration.ofSeconds(2));
        assertTrue(watcher.poll().isEmpty(), "too soon");
        clock.advance(Duration.ofSeconds(4));
        assertEquals(1, watcher.poll().size());
    }

    /**
    * Tests that when a project's parent directory vanishes (simulating an unplugged external drive), 
    * it is not incorrectly mistaken for an intentional project deletion.
    */
    @Test
    void missingParentFolderIsNotTreatedAsADeletion() {
        ProjectFolderWatcher watcher = newWatcher();
        watcher.track("Gone", root.resolve("unplugged-drive").resolve("Project"));
        assertTrue(watcher.poll().isEmpty());
        assertEquals(1, watcher.trackedCount());
    }

    /**
    * Tests that tracking the same physical folder path multiple times is de-duplicated 
    * and counted as a single tracked entry.
    * @throws IOException if file system operations fail
    */
    @Test
    void sameFolderTrackedTwiceCountsOnce() throws IOException {
        ProjectFolderWatcher watcher = newWatcher();
        Path folder = project("P", false);
        watcher.track("P", folder);
        watcher.track("P", folder.resolve("..").resolve("P"));
        assertEquals(1, watcher.trackedCount());
    }

    /**
    * Tests that project deletions occurring while BlueJ was closed are successfully noticed 
    * and reported during the next application session.
    * @throws IOException if file system operations fail
    */
    @Test
    void deletionWhileBlueJWasClosedIsNoticedInTheNextSession() throws IOException {
        Path folder = project("Homework", false);
        newWatcher().track("Homework", folder);
        Files.delete(folder);

        ProjectFolderWatcher nextSession = newWatcher();
        List<TelemetryEvent> events = nextSession.poll();

        assertEquals(1, events.size());
        assertEquals(EventType.PROJECT_DELETED, events.get(0).type());
        assertEquals("Homework", events.get(0).getString("project", ""));
        assertEquals(0, newWatcher().trackedCount(), "forgotten once reported");
    }

    /**
    * Tests that remembered projects from prior sessions that still exist on disk 
    * are correctly retained in the tracking state.
    * @throws IOException if file system operations fail
    */
    @Test
    void rememberedProjectsThatStillExistAreKept() throws IOException {
        newWatcher().track("Keep", project("Keep", false));

        ProjectFolderWatcher nextSession = newWatcher();

        assertEquals(1, nextSession.trackedCount());
        assertTrue(nextSession.poll().isEmpty());
    }

    /**
    * Tests that corrupted or unreadable registry files do not crash the watcher 
    * and are safely ignored with a zero tracked count.
    * @throws IOException if file system operations fail
    */
    @Test
    void unreadableRegistryIsIgnored() throws IOException {
        Files.writeString(registry, "no tab here\n\t\nbroken");
        assertEquals(0, newWatcher().trackedCount());
    }
}