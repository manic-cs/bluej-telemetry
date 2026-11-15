package edu.uwi.telemetry.service;

import edu.uwi.telemetry.export.ExportOutcome;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Recovers telemetry sessions that were not properly completed.
 * Checks the log directory for unfinished sessions, attempts to
 * finalise them as interrupted, and deletes their log files
 * after successful recovery.
 *
 * @author Micah Mitchell
 */

public final class SessionRecovery {
    private final Path logDirectory;
    private final Path currentLog;
    private final SessionFinaliser finaliser;

    public SessionRecovery(Path logDirectory, Path currentLog, SessionFinaliser finaliser) {
        this.logDirectory = logDirectory;
        this.currentLog = currentLog;
        this.finaliser = finaliser;
    }

    public int recoverAll() {
        if (!Files.isDirectory(logDirectory)) {
            return 0;
        }

        int recovered = 0;
        try (DirectoryStream<Path> logs = Files.newDirectoryStream(logDirectory, "*.log")) {
            for (Path log : logs) {
                if (currentLog != null && log.toAbsolutePath().equals(currentLog.toAbsolutePath())) {
                    continue;
                }

                if (recover(log)) {
                    recovered++;
                }
            }
        }

        catch (IOException ex) {
        }

        return recovered;
    }

    private boolean recover(Path log) {
        ExportOutcome result = finaliser.finalise(log, null, true);
        if (!result.success()) {
            return false;
        }

        try {
            Files.deleteIfExists(log);
        }

        catch (IOException ex) {
            return false;
        }

        return true;
    }
}