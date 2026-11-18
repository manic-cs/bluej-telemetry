package edu.uwi.telemetry.workspace;

/**
 * Detects project creation and deletion, which BlueJ does not report, by watching the
 * project folders.
 * <ul>
 *   <li><b>Created:</b> when a project is opened and its {@code package.bluej} file was created seconds
 *       earlier, the project was just created (File &gt; New Project).</li>
 *   <li><b>Deleted:</b> every opened project folder is remembered, also across sessions in a small file on
 *       this computer. A remembered folder that disappears (while its parent folder is still there, so an
 *       unplugged drive is not mistaken for a deletion) is reported once as PROJECT_DELETED, whether it
 *       vanished while BlueJ was running or while it was closed.</li>
 * </ul>
 * @author Rinard Guy
 */

import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.io.FileSupport;
import edu.uwi.telemetry.model.TelemetryEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProjectFolderWatcher implements PeriodicCheck {
    static final String PROJECT_MARKER_FILE = "package.bluej";
    static final Duration CREATION_WINDOW = Duration.ofSeconds(15);

    private final TelemetryEventFactory factory;
    private final Clock clock;
    private final Duration minInterval;
    private final Path registryFile;
    private final Map<Path, String> tracked = new LinkedHashMap<>();
    private final List<TelemetryEvent> pending = new ArrayList<>();
    private Instant lastCheck;

    /** @param registryFile where remembered project folders are kept between sessions; null keeps them in memory only */
    public ProjectFolderWatcher(TelemetryEventFactory factory, Clock clock, Duration minInterval, Path registryFile) {
        this.factory = factory;
        this.clock = clock;
        this.minInterval = minInterval;
        this.registryFile = registryFile;
        loadRegistry();
    }

    /** Call when a project is opened. Reports its creation if it is brand new and remembers its folder. */
    public synchronized void track(String projectName, Path folder) {
        Path key = folder.toAbsolutePath().normalize();
        if (tracked.containsKey(key)) {
            return;
        }

        if (wasJustCreated(key)) {
            pending.add(factory.projectCreated(projectName));
        }

        tracked.put(key, projectName);
        saveRegistry();
    }

    public synchronized int trackedCount() {
        return tracked.size();
    }

    @Override
    public synchronized List<TelemetryEvent> poll() {
        List<TelemetryEvent> events = new ArrayList<>(pending);
        pending.clear();
        Instant now = clock.instant();

        if (lastCheck == null || Duration.between(lastCheck, now).compareTo(minInterval) >= 0) {
            lastCheck = now;
            events.addAll(findDeletedProjects());
        }

        return events;
    }

    private List<TelemetryEvent> findDeletedProjects() {
        List<TelemetryEvent> deleted = new ArrayList<>();
        Iterator<Map.Entry<Path, String>> iterator = tracked.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Path, String> entry = iterator.next();
            Path parent = entry.getKey().getParent();

            if (!Files.exists(entry.getKey()) && parent != null && Files.exists(parent)) {
                deleted.add(factory.projectDeleted(entry.getValue()));
                iterator.remove();
            }
        }

        if (!deleted.isEmpty()) {
            saveRegistry();
        }

        return deleted;
    }

    /** BlueJ writes package.bluej when it creates a project; a recent creation time means a new project. */
    private boolean wasJustCreated(Path folder) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(folder.resolve(PROJECT_MARKER_FILE), BasicFileAttributes.class);
            Duration age = Duration.between(attributes.creationTime().toInstant(), clock.instant());
            return age.compareTo(CREATION_WINDOW) <= 0;
        }

        catch (IOException ex) {
            return false;
        }
    }

    private void loadRegistry() {
        if (registryFile == null || !Files.isRegularFile(registryFile)) {
            return;
        }

        try {
            for (String line : Files.readAllLines(registryFile, StandardCharsets.UTF_8)) {
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    tracked.put(Path.of(line.substring(0, tab)), line.substring(tab + 1));
                }
            }
        }

        catch (IOException | RuntimeException ex) {
        }
    }

    private void saveRegistry() {
        if (registryFile == null) {
            return;
        }

        StringBuilder text = new StringBuilder();
        tracked.forEach((folder, name) -> text.append(folder).append('\t').append(name).append('\n'));

        try {
            FileSupport.writeAtomically(registryFile, text.toString());
        }

        catch (IOException ex) {
        }
    }
}