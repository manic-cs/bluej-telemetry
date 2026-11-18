package edu.uwi.telemetry.bluej;

/**
 * Entry point of the plugin: the class BlueJ loads, named in the jar's {@code Main-Class} manifest attribute.
 * On startup it reads the student id and report folder from the preferences, builds the
 * {@link TelemetryService} and its helpers (event factory, edit tracker, project folder watcher and javac
 * diagnostic enricher), registers the preferences panel, the Tools menu item and the BlueJ listeners,
 * and starts recording. On terminate it stops the helpers and ends the session, which writes the JSON
 * and PDF reports. A JVM shutdown hook calls the same end method as a safety net.
 * Needs Extensions API 3.3 (BlueJ 5.0.3) for the editor document listener.
 * @author Rinard Guy
 */

import bluej.extensions2.BlueJ;
import bluej.extensions2.Extension;
import bluej.extensions2.BProject;
import edu.uwi.telemetry.diagnostics.DiagnosticEnricher;
import edu.uwi.telemetry.diagnostics.JavacDiagnosticRunner;
import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.workspace.EditActivityTracker;
import edu.uwi.telemetry.workspace.ProjectFolderWatcher;
import edu.uwi.telemetry.service.TelemetryConfig;
import edu.uwi.telemetry.service.TelemetryService;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

public class TelemetryExtension extends Extension {
    private TelemetryService service;
    private DiagnosticEnricher diagnosticEnricher;
    private ExecutionResultHandler executionResultHandler;

    @Override
    public void startup(BlueJ bluej) {
        try {
            String studentId = bluej.getExtensionPropertyString(TelemetryPreferences.STUDENT_ID_KEY, "");
            String reportDir = bluej.getExtensionPropertyString(TelemetryPreferences.OUTPUT_DIR_KEY, "");
            TelemetryConfig config = TelemetryConfig.resolve(studentId, reportDir, Path.of(System.getProperty("user.home")));
            Clock clock = Clock.systemUTC();
            TelemetryEventFactory factory = new TelemetryEventFactory(clock);
            EditActivityTracker editTracker = new EditActivityTracker(factory);
            service = new TelemetryService(config, new BlueJWorkspaceSource(bluej, editTracker), clock, factory);
            service.addPeriodicCheck(editTracker);

            ProjectFolderWatcher folderWatcher = new ProjectFolderWatcher(factory, clock, Duration.ofSeconds(5), config.knownProjectsFile());
            service.addPeriodicCheck(folderWatcher);
            diagnosticEnricher = DiagnosticEnricher.withBackgroundThread(new JavacDiagnosticRunner(), factory, service);

            bluej.setPreferenceGenerator(new TelemetryPreferences(bluej));
            bluej.setMenuGenerator(new TelemetryMenu(service));

            if (!service.start()) {
                return;
            }

            bluej.addPackageListener(new PackageEventHandler(service, factory, folderWatcher));
            bluej.addClassListener(new ClassEventHandler(service, factory));
            bluej.addCompileListener(new CompileEventHandler(service, factory, diagnosticEnricher));
            bluej.addInvocationFinishedListener(new InvocationEventHandler(service, factory));
            executionResultHandler = new ExecutionResultHandler(service, factory);
            executionResultHandler.register();
            trackAlreadyOpenProjects(bluej, folderWatcher);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> service.end(), "telemetry-shutdown"));
        }

        catch (RuntimeException ex) {
            System.err.println("[BlueJ Telemetry] failed to start: " + ex);
        }
    }

    /** Projects that were already open when the extension loaded never fire packageOpened. */
    private static void trackAlreadyOpenProjects(BlueJ bluej, ProjectFolderWatcher watcher) {
        try {
            for (BProject project : bluej.getOpenProjects()) {
                watcher.track(project.getName(), project.getDir().toPath());
            }
        }

        catch (Exception ex) {
        }
    }

    @Override
    public void terminate() {
        if (executionResultHandler != null) {
            executionResultHandler.unregister();
        }

        if (diagnosticEnricher != null) {
            diagnosticEnricher.close();
        }

        if (service != null) {
            service.end();
        }
    }

    /** Needs Extensions API 3.3 (BlueJ 5.0.3) for JavaEditor.addDocumentListener(). */
    @Override
    public boolean isCompatible() {
        int major = getExtensionsAPIVersionMajor();
        return major > 3 || (major == 3 && getExtensionsAPIVersionMinor() >= 3);
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getName() {
        return "BlueJ Telemetry";
    }

    @Override
    public String getDescription() {
        return "Records compilation, execution and editing activity per session and exports JSON and PDF reports.";
    }

    @Override
    public URL getURL() {
        try {
            return URI.create("https://www.bluej.org/extensions/extensions2.html").toURL();
        } catch (Exception ex) {
            return null;
        }
    }
}