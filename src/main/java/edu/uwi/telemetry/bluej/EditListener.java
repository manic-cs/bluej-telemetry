package edu.uwi.telemetry.bluej;

/**
 * Adapter for BlueJ's editor {@link DocumentListener} (Extensions API 3.3).
 * BlueJ calls it on the JavaFX thread for every text change and hands over the replaced and the new text.
 * Only the two lengths are used: the text is not stored, logged or looked at, and the method does no more
 * work than passing two numbers to the {@link EditActivityTracker}.
 * @author Rinard Guy
 */

import bluej.extensions2.editor.DocumentListener;
import edu.uwi.telemetry.workspace.EditActivityTracker;

public final class EditListener implements DocumentListener {
    private final EditActivityTracker tracker;
    private final String project;
    private final String className;

    public EditListener(EditActivityTracker tracker, String project, String className) {
        this.tracker = tracker;
        this.project = project;
        this.className = className;
    }

    @Override
    public void textReplaced(int origStartIncl, String replaced, String replacement, int linesRemoved, int linesAdded) {
        Safe.run(() -> tracker.record(project, className, length(replaced), length(replacement)));
    }

    private static int length(String text) {
        return text == null ? 0 : text.length();
    }
}