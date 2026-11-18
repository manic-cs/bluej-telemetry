package edu.uwi.telemetry.bluej;

/**
 * Adds Tools &gt; Export Telemetry Report to BlueJ.
 * Choosing it runs {@link TelemetryService.exportNow()} on a background thread, so the report for the
 * session so far is written while the session keeps running, and then shows an alert listing the files
 * written or the problems found.
 * @author Rinard Guy
 */

import bluej.extensions2.BPackage;
import bluej.extensions2.MenuGenerator;
import edu.uwi.telemetry.export.ExportOutcome;
import edu.uwi.telemetry.service.TelemetryService;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuItem;

public final class TelemetryMenu extends MenuGenerator {
    private final TelemetryService service;

    public TelemetryMenu(TelemetryService service) {
        this.service = service;
    }

    @Override
    public MenuItem getToolsMenuItem(BPackage aPackage) {
        MenuItem item = new MenuItem("Export Telemetry Report");
        item.setOnAction(event -> exportInBackground());

        return item;
    }

    private void exportInBackground() {
        Thread thread = new Thread(() -> {
            ExportOutcome outcome = service.exportNow();
            Platform.runLater(() -> show(outcome));
        }, "telemetry-manual-export");
        thread.setDaemon(true);
        thread.start();
    }

    private void show(ExportOutcome outcome) {
        Alert alert = new Alert(outcome.success() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING, outcome.describe(), ButtonType.OK);
        alert.setHeaderText("Telemetry export");
        alert.show();
    }
}