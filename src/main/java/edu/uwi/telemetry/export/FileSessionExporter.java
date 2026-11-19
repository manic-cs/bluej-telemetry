package edu.uwi.telemetry.export;

import edu.uwi.telemetry.io.FileSupport;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Provides the shared file-writing process for session report exporters.
 * 
 * @author Sawdah Kadir
 */
public abstract class FileSessionExporter implements SessionExporter {
    private final Path outputDirectory;

    protected FileSessionExporter(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * Returns the file extension used by the concrete exporter.
     */
    protected abstract String fileExtension();

    /**
     * Writes the supplied session report to the specified file.
     */

    protected abstract void writeTo(SessionReport report, Path file) throws IOException;

    /**
     * Exports a session report using a temporary file before replacing
     * the target file.
     */
    @Override
    public final Path export(SessionReport report) throws IOException {
        Path target = outputDirectory.resolve("session-" + report.data().sessionId() + "." + fileExtension());
        Path temporary = FileSupport.temporaryFor(target);
        writeTo(report, temporary);
        FileSupport.replace(temporary, target);

        return target;
    }
}