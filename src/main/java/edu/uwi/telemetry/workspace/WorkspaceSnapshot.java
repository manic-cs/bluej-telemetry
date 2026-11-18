package edu.uwi.telemetry.workspace;

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