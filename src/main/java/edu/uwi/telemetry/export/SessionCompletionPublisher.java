package edu.uwi.telemetry.export;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Publishes completed session reports to registered exporters.
 * 
 * @author Sawdah Kadir
 */
public final class SessionCompletionPublisher {
    private final List<SessionExporter> subscribers = new CopyOnWriteArrayList<>();

    public void subscribe(SessionExporter exporter) {
        subscribers.add(Objects.requireNonNull(exporter));
    }

    public int subscriberCount() {
        return subscribers.size();
    }

    public ExportOutcome publish(SessionReport report) {
        List<Path> files = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (SessionExporter exporter : subscribers) {
            try {
                files.add(exporter.export(report));
            }

            catch (Exception ex) {
                problems.add(exporter.formatName() + " export failed: " + ex.getMessage());
            }
        }

        return new ExportOutcome(files, problems);
    }
}