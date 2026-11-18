package edu.uwi.telemetry.bluej;

/**
 * Adapter between BlueJ's {@code PackageListener} and the plugin's {@link EventSink}.
 * When a project (package) is opened it creates a PROJECT_OPENED event carrying the class count and tells
 * the {@link ProjectFolderWatcher} about the project's folder; when a project is closing it creates a
 * PROJECT_CLOSED event. Failures are swallowed through {@link Safe} so BlueJ is never disturbed.
 * @author Rinard Guy
 */

import bluej.extensions2.BPackage;
import bluej.extensions2.event.PackageEvent;
import bluej.extensions2.event.PackageListener;
import edu.uwi.telemetry.event.EventSink;
import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.workspace.ProjectFolderWatcher;

public final class PackageEventHandler implements PackageListener {
    private final EventSink sink;
    private final TelemetryEventFactory factory;
    private final ProjectFolderWatcher folderWatcher;

    public PackageEventHandler(EventSink sink, TelemetryEventFactory factory, ProjectFolderWatcher folderWatcher) {
        this.sink = sink;
        this.factory = factory;
        this.folderWatcher = folderWatcher;
    }

    @Override
    public void packageOpened(PackageEvent event) {
        Safe.run(() -> {
            try {
                BPackage pkg = event.getPackage();
                String name = pkg.getProject().getName();
                sink.accept(factory.projectOpened(name, pkg.getClasses().length));
                folderWatcher.track(name, pkg.getProject().getDir().toPath());
            }

            catch (Exception ex) {
            }
        });
    }

    @Override
    public void packageClosing(PackageEvent event) {
        Safe.run(() -> {
            try {
                sink.accept(factory.projectClosed(event.getPackage().getProject().getName()));
            }

            catch (Exception ex) {
            }
        });
    }
}