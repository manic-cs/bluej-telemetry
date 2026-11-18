package edu.uwi.telemetry.workspace;

/**
 * Extra work that runs on every sampler tick and may produce events, for example the
 * project folder checks and the typing summaries. Implemented by {@link ProjectFolderWatcher} and
 * {@link EditActivityTracker}. New checks can be added to the {@link WorkspaceSampler} without changing it.
 * @author Rinard Guy
 */

import edu.uwi.telemetry.model.TelemetryEvent;
import java.util.List;

public interface PeriodicCheck {
    List<TelemetryEvent> poll();
}