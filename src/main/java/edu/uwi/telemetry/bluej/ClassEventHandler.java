package edu.uwi.telemetry.bluej;

/**
 * Adapter between BlueJ's {@link ClassListener} and the plugin's {@link EventSink}.
 * A class that is neither compiled nor in error has been edited since its last compile, so
 * {@link classStateChanged} creates a CLASS_MODIFIED event for it. Name changes and removals are left to the
 * {@link WorkspaceDiffer}, which detects class creation and deletion from snapshots, so that logic lives in
 * one place (a rename therefore appears as one deletion plus one creation).
 * @author Rinard Guy
 */

import bluej.extensions2.event.ClassEvent;
import bluej.extensions2.event.ClassListener;
import edu.uwi.telemetry.event.EventSink;
import edu.uwi.telemetry.event.TelemetryEventFactory;

public final class ClassEventHandler implements ClassListener {
    private final EventSink sink;
    private final TelemetryEventFactory factory;

    public ClassEventHandler(EventSink sink, TelemetryEventFactory factory) {
        this.sink = sink;
        this.factory = factory;
    }

    @Override
    public void classStateChanged(ClassEvent event) {
        Safe.run(() -> {
            if (event.isClassCompiled() || event.hasError()) {
                return;
            }

            try {
                String project = event.getPackage().getProject().getName();
                sink.accept(factory.classModified(project, event.getBClass().getName()));
            }

            catch (Exception ex) {
            }
        });
    }

    @Override
    public void classNameChanged(ClassEvent event) {
        // A rename shows up as one deletion and one creation in the sampler.
    }

    @Override
    public void classRemoved(ClassEvent event) {
        // Detected by the sampler, which keeps all creation and deletion logic in one place.
    }
}