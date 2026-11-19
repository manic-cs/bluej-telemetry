package edu.uwi.telemetry.export;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Defines the contract for exporting a session report to a file.
 * 
 * @author Sawdah Kadir
 */
public interface SessionExporter {
    Path export(SessionReport report) throws IOException;
    default String formatName() {
        return getClass().getSimpleName();
    }
}