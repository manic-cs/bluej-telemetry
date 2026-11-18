package edu.uwi.telemetry.workspace;

/**
 * Compares consecutive workspace snapshots and turns the differences into events: new or
 * deleted classes, changed method counts and cursor or line-count changes in an editor.
 * The first time a project is seen it is only a baseline, so opening an existing project does not look like
 * creating all its classes; a project that disappears is forgotten, so reopening it is a new baseline.
 * It remembers the previous state and is used only by the sampler thread, so it needs no locking.
 * @author Rinard Guy
 */

import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.model.EditorSample;
import edu.uwi.telemetry.model.TelemetryEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class WorkspaceDiffer {
    private static final class ProjectState {
        final Set<String> classes = new LinkedHashSet<>();
        final Map<String, Integer> methodCounts = new HashMap<>();
    }

    private final TelemetryEventFactory factory;
    private final Map<String, ProjectState> projects = new HashMap<>();
    private final Map<String, EditorSample> lastEditors = new HashMap<>();

    public WorkspaceDiffer(TelemetryEventFactory factory) {
        this.factory = Objects.requireNonNull(factory);
    }

    public List<TelemetryEvent> diff(WorkspaceSnapshot snapshot) {
        List<TelemetryEvent> events = new ArrayList<>();
        diffClasses(snapshot, events);
        diffEditors(snapshot, events);

        return events;
    }

    private void diffClasses(WorkspaceSnapshot snapshot, List<TelemetryEvent> events) {
        Map<String, List<ClassInfo>> byProject = new HashMap<>();
        for (ClassInfo info : snapshot.classes()) {
            byProject.computeIfAbsent(info.project(), p -> new ArrayList<>()).add(info);
        }

        for (String project : snapshot.projects()) {
            List<ClassInfo> current = byProject.getOrDefault(project, List.of());
            ProjectState state = projects.get(project);

            boolean baseline = state == null;

            if (baseline) {
                state = new ProjectState();
                projects.put(project, state);
            }

            Set<String> currentNames = new LinkedHashSet<>();

            for (ClassInfo info : current) {
                currentNames.add(info.className());
            }

            if (!baseline) {
                for (String name : currentNames) {
                    if (!state.classes.contains(name)) {
                        events.add(factory.classCreated(project, name));
                    }
                }

                for (String name : state.classes) {
                    if (!currentNames.contains(name)) {
                        events.add(factory.classDeleted(project, name));
                        state.methodCounts.remove(name);
                    }
                }
            }

            state.classes.clear();
            state.classes.addAll(currentNames);

            for (ClassInfo info : current) {
                diffMethodCount(project, info, state, baseline, events);
            }
        }
        projects.keySet().retainAll(snapshot.projects());
    }

    private void diffMethodCount(String project, ClassInfo info, ProjectState state, boolean baseline, List<TelemetryEvent> events) {
        if (info.methodCount() < 0) {
            return;
        }

        Integer previous = state.methodCounts.put(info.className(), info.methodCount());

        if (baseline) {
            return;
        }

        int before = previous == null ? 0 : previous;

        if (info.methodCount() != before) {
            events.add(factory.methodCountChanged(project, info.className(), info.methodCount()));
        }
    }

    private void diffEditors(WorkspaceSnapshot snapshot, List<TelemetryEvent> events) {
        Set<String> seen = new HashSet<>();
        for (EditorSample sample : snapshot.editors()) {
            String key = sample.project() + "\u0000" + sample.className();
            seen.add(key);
            EditorSample previous = lastEditors.put(key, sample);

            if (previous != null && !previous.equals(sample)) {
                events.add(factory.editorSample(sample));
            }
        }
        lastEditors.keySet().retainAll(seen);
    }
}