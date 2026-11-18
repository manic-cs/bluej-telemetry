package edu.uwi.telemetry.bluej;

/**
 * Implementation of {@link WorkspaceSource} on top of BlueJ's Extensions API.
 * Called every second by the {@link WorkspaceSampler}; it moves to the JavaFX thread (and waits up to two
 * seconds) and reads the open projects and their classes, each class's method count (cached until its
 * .class file changes) and, for every open Java editor, the cursor line and column and the line count.
 * It also registers one {@link EditListener} per open Java editor and removes it when the editor closes.
 * The source text itself is never requested.
 * @author Rinard Guy
 */

import bluej.extensions2.BClass;
import bluej.extensions2.BPackage;
import bluej.extensions2.BProject;
import bluej.extensions2.BlueJ;
import bluej.extensions2.editor.DocumentListener;
import bluej.extensions2.editor.JavaEditor;
import bluej.extensions2.editor.TextLocation;
import edu.uwi.telemetry.workspace.ClassInfo;
import edu.uwi.telemetry.workspace.EditActivityTracker;
import edu.uwi.telemetry.model.EditorSample;
import edu.uwi.telemetry.workspace.WorkspaceSnapshot;
import edu.uwi.telemetry.workspace.WorkspaceSource;
import javafx.application.Platform;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class BlueJWorkspaceSource implements WorkspaceSource {
    private record CachedCount(long classFileStamp, int count) {}
    private record Registration(JavaEditor editor, DocumentListener listener) {}

    private final BlueJ bluej;
    private final EditActivityTracker editTracker;
    private final Map<String, CachedCount> methodCountCache = new HashMap<>();
    private final Map<String, Registration> registrations = new HashMap<>();

    public BlueJWorkspaceSource(BlueJ bluej, EditActivityTracker editTracker) {
        this.bluej = bluej;
        this.editTracker = editTracker;
    }

    @Override
    public WorkspaceSnapshot capture() {
        if (Platform.isFxApplicationThread()) {
            return captureNow();
        }

        FutureTask<WorkspaceSnapshot> task = new FutureTask<>(this::captureNow);

        try {
            Platform.runLater(task);
            return task.get(2, TimeUnit.SECONDS);
        }

        catch (Exception ex) {
            task.cancel(false);
            return null; // skip this tick
        }
    }

    private WorkspaceSnapshot captureNow() {
        Set<String> projects = new LinkedHashSet<>();
        List<ClassInfo> classes = new ArrayList<>();
        List<EditorSample> editors = new ArrayList<>();
        Set<String> editorsSeen = new LinkedHashSet<>();

        for (BProject project : bluej.getOpenProjects()) {
            try {
                String projectName = project.getName();
                List<ClassInfo> projectClasses = new ArrayList<>();
                List<EditorSample> projectEditors = new ArrayList<>();

                for (BPackage pkg : project.getPackages()) {
                    for (BClass bClass : pkg.getClasses()) {
                        String className = bClass.getName();
                        projectClasses.add(new ClassInfo(projectName, className, methodCount(projectName, bClass)));
                        JavaEditor editor = bClass.getJavaEditorIfOpen();

                        if (editor != null) {
                            listenForEdits(projectName, className, editor, editorsSeen);
                            TextLocation caret = editor.getCaretLocation();
                            projectEditors.add(new EditorSample(projectName, className, caret.getLine(), caret.getColumn(), editor.getLineCount()));
                        }
                    }
                }
                // Only add a project once it was read completely, so a project closing
                // mid-scan is not mistaken for deleted classes.
                projects.add(projectName);
                classes.addAll(projectClasses);
                editors.addAll(projectEditors);

            }

            catch (Exception ex) {
            }
        }

        stopListeningToClosedEditors(editorsSeen);

        return new WorkspaceSnapshot(projects, classes, editors);
    }

    /** Registers BlueJ's document listener once for each open Java editor. */
    private void listenForEdits(String projectName, String className, JavaEditor editor, Set<String> seen) {
        String key = projectName + "\u0000" + className;
        seen.add(key);

        if (!registrations.containsKey(key)) {
            DocumentListener listener = new EditListener(editTracker, projectName, className);
            editor.addDocumentListener(listener);
            registrations.put(key, new Registration(editor, listener));
        }
    }

    /** Removes the listener of editors that are no longer open, so a reopened editor is not counted twice. */
    private void stopListeningToClosedEditors(Set<String> seen) {
        registrations.entrySet().removeIf(entry -> {
            if (seen.contains(entry.getKey())) {
                return false;
            }

            try {
                entry.getValue().editor().removeDocumentListener(entry.getValue().listener());
            }

            catch (RuntimeException ex) {
            }

            return true;
        });
    }
    
    /** Method count of a compiled class, cached until the .class file changes; -1 if unknown. */
    private int methodCount(String project, BClass bClass) {
        try {
            if (!bClass.isCompiled()) {
                return -1;
            }

            File classFile = bClass.getClassFile();
            long stamp = classFile == null ? 0L : classFile.lastModified();
            String key = project + "\u0000" + bClass.getName();
            CachedCount cached = methodCountCache.get(key);

            if (cached != null && cached.classFileStamp() == stamp) {
                return cached.count();
            }

            int count = bClass.getDeclaredMethods().length;
            methodCountCache.put(key, new CachedCount(stamp, count));

            return count;
        }

        catch (Exception ex) {
            return -1;
        }
    }
}