package edu.uwi.telemetry.workspace;

/**
 * Immutable record of what BlueJ looks like at one instant: the names of the open
 * projects, their classes and the open editors (cursor position and line count).
 * Created by a {@code WorkspaceSource} and consumed by the {@code WorkspaceDiffer}. The compact constructor
 * makes unmodifiable copies, so a snapshot can be handed from BlueJ's UI thread to the sampler thread
 * without locking.
 * @author Rinard Guy
 */

import edu.uwi.telemetry.model.EditorSample;
import java.util.List;
import java.util.Set;

public record WorkspaceSnapshot(Set<String> projects, List<ClassInfo> classes, List<EditorSample> editors) {
    public WorkspaceSnapshot {
        projects = Set.copyOf(projects);
        classes = List.copyOf(classes);
        editors = List.copyOf(editors);
    }

    public static WorkspaceSnapshot empty() {
        return new WorkspaceSnapshot(Set.of(), List.of(), List.of());
    }
}