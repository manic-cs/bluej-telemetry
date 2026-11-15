package edu.uwi.telemetry.service;

import edu.uwi.telemetry.analysis.SessionAnalyzer;
import edu.uwi.telemetry.export.ExportOutcome;
import edu.uwi.telemetry.export.SessionCompletionPublisher;
import edu.uwi.telemetry.export.SessionReport;
import edu.uwi.telemetry.log.EventLogReader;
import edu.uwi.telemetry.model.SessionData;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handles the completion of a telemetry session by reading its event log,
 * analysing the recorded activity, and generating session reports.
 * Supports running the process in a separate thread with a time limit
 * and returns information about any errors that occur.
 *
 * @author Micah Mitchell
 */

public final class SessionFinaliser {
    private static final long TASK_TIMEOUT_SECONDS = 20;

    private final EventLogReader reader;
    private final SessionAnalyzer analyzer;
    private final SessionCompletionPublisher publisher;

    public SessionFinaliser(EventLogReader reader, SessionAnalyzer analyzer, SessionCompletionPublisher publisher) {
        this.reader = Objects.requireNonNull(reader);
        this.analyzer = Objects.requireNonNull(analyzer);
        this.publisher = Objects.requireNonNull(publisher);
    }

    public ExportOutcome finalise(Path logFile, Instant end, boolean interrupted) {
        try {
            SessionData data = reader.read(logFile).withEnd(end, interrupted);
            return publisher.publish(new SessionReport(data, analyzer.analyze(data)));
        }

        catch (IOException | RuntimeException ex) {
            return failed("Could not read the session log: " + ex.getMessage());
        }
    }

    public ExportOutcome finaliseAsTask(Path logFile, Instant end, boolean interrupted) {
        return runAsSeparateTask(() -> finalise(logFile, end, interrupted));
    }

    private static ExportOutcome runAsSeparateTask(Callable<ExportOutcome> task) {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "telemetry-finaliser");
            thread.setDaemon(true);
            return thread;
        });

        try {
            return executor.submit(task).get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failed("Interrupted while writing the reports.");
        }

        catch (ExecutionException | TimeoutException ex) {
            return failed("Could not write the reports: " + ex.getMessage());
        }

        finally {
            executor.shutdownNow();
        }
    }

    private static ExportOutcome failed(String problem) {
        return new ExportOutcome(List.of(), List.of(problem));
    }
}