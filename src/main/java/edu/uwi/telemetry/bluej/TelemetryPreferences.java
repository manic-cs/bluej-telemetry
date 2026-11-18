package edu.uwi.telemetry.bluej;

/**
 * Preferences panel (Tools &gt; Preferences &gt; Extensions) where the student sets the
 * telemetry student id and the folder for the reports. The values are stored as BlueJ extension properties
 * and are read when the extension starts, so a change applies the next time BlueJ starts.
 * @author Rinard Guy
 */

import bluej.extensions2.BlueJ;
import bluej.extensions2.PreferenceGenerator;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;

public final class TelemetryPreferences implements PreferenceGenerator {
    public static final String STUDENT_ID_KEY = "studentId";
    public static final String OUTPUT_DIR_KEY = "reportDirectory";

    private final BlueJ bluej;
    private final Pane pane = new Pane();
    private final TextField studentId = new TextField();
    private final TextField reportDirectory = new TextField();

    public TelemetryPreferences(BlueJ bluej) {
        this.bluej = bluej;
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label("Telemetry student ID:"), 0, 0);
        grid.add(studentId, 1, 0);
        grid.add(new Label("Report folder (blank = default):"), 0, 1);
        grid.add(reportDirectory, 1, 1);
        grid.add(new Label("Changes apply the next time BlueJ starts."), 0, 2, 2, 1);
        pane.getChildren().add(grid);
        loadValues();
    }

    @Override
    public Pane getWindow() {
        return pane;
    }

    @Override
    public void saveValues() {
        bluej.setExtensionPropertyString(STUDENT_ID_KEY, studentId.getText());
        bluej.setExtensionPropertyString(OUTPUT_DIR_KEY, reportDirectory.getText());
    }

    @Override
    public void loadValues() {
        studentId.setText(bluej.getExtensionPropertyString(STUDENT_ID_KEY, ""));
        reportDirectory.setText(bluej.getExtensionPropertyString(OUTPUT_DIR_KEY, ""));
    }
}