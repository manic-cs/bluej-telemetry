package edu.uwi.telemetry.service;

import edu.uwi.telemetry.event.EventSink;
import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.analysis.SessionAnalyzer;
import edu.uwi.telemetry.export.ExportOutcome;
import edu.uwi.telemetry.export.JsonSessionExporter;
import edu.uwi.telemetry.export.PdfReportExporter;
import edu.uwi.telemetry.export.SessionCompletionPublisher;
import edu.uwi.telemetry.log.BackgroundLogWriter;
import edu.uwi.telemetry.log.EventLog;
import edu.uwi.telemetry.log.EventLogReader;
import edu.uwi.telemetry.model.SessionHeader;
import edu.uwi.telemetry.model.TelemetryEvent;
import edu.uwi.telemetry.queue.BoundedEventQueue;
import edu.uwi.telemetry.workspace.PeriodicCheck;
import edu.uwi.telemetry.workspace.WorkspaceDiffer;
import edu.uwi.telemetry.workspace.WorkspaceSampler;
import edu.uwi.telemetry.workspace.WorkspaceSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages telemetry sessions by coordinating activity tracking,
 * event logging, session recovery, and report generation.
 *
 * @author Micah Mitchell
 */

public final class TelemetryService implements EventSink {
    private static final long WRITER_STOP_MILLIS = 5000;

    private record Session(String id, Path logFile, BackgroundLogWriter writer) {}

    private final TelemetryConfig config;
    private final Clock clock;
    private final TelemetryEventFactory factory;
    private final BoundedEventQueue queue;
    private final SessionFinaliser finaliser;
    private final WorkspaceSampler sampler;

    private Session session;
    private ExportOutcome endOutcome;

    public TelemetryService(TelemetryConfig config, WorkspaceSource source, Clock clock) {
        this(config, source, clock, defaultPublisher(config.reportDirectory()));
    }

    public TelemetryService(TelemetryConfig config, WorkspaceSource source, Clock clock, SessionCompletionPublisher publisher) {
        this(config, source, clock, new TelemetryEventFactory(clock), publisher);
    }

    public TelemetryService(TelemetryConfig config, WorkspaceSource source, Clock clock, TelemetryEventFactory factory) {
        this(config, source, clock, factory, defaultPublisher(config.reportDirectory()));
    }

    private TelemetryService(TelemetryConfig config, WorkspaceSource source, Clock clock, TelemetryEventFactory factory, SessionCompletionPublisher publisher) {
        this.config = config;
        this.clock = clock;
        this.factory = factory;
        this.queue = new BoundedEventQueue(config.queueCapacity());
        this.finaliser = new SessionFinaliser(new EventLogReader(), new SessionAnalyzer(config.idleThreshold()), publisher);
        this.sampler = new WorkspaceSampler(source, new WorkspaceDiffer(factory), this, config.samplePeriod());
    }

    public static SessionCompletionPublisher defaultPublisher(Path reportDirectory) {
        SessionCompletionPublisher publisher = new SessionCompletionPublisher();
        publisher.subscribe(new JsonSessionExporter(reportDirectory));
        publisher.subscribe(new PdfReportExporter(reportDirectory));

        return publisher;
    }

    static String newSessionId(Clock clock) {
        String time = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(clock.instant());

        return "S" + time + "-" + String.format("%06x", ThreadLocalRandom.current().nextInt(0x1000000));
    }

    public TelemetryEventFactory events() {
        return factory;
    }

    public synchronized boolean isActive() {
        return session != null;
    }

    public void addPeriodicCheck(PeriodicCheck check) {
        sampler.addPeriodicCheck(check);
    }

    public synchronized boolean start() {
        if (session != null || endOutcome != null) {
            return session != null;
        }

        try {
            String id = newSessionId(clock);
            Path logFile = config.logDirectory().resolve("session-" + id + ".log");
            EventLog log = new EventLog(logFile);
            log.writeHeader(new SessionHeader(id, config.studentId(), clock.instant()));
            session = new Session(id, logFile, new BackgroundLogWriter(queue, log, config.sampleBatchSize(), 5000));
        }

        catch (IOException ex) {
            System.err.println("[BlueJ Telemetry] Cannot create session log: " + ex.getMessage());
            return false;
        }
        session.writer().start();
        sampler.start();
        startRecovery(session.logFile());

        return true;
    }

    private void startRecovery(Path currentLog) {
        SessionRecovery recovery = new SessionRecovery(config.logDirectory(), currentLog, finaliser);
        Thread thread = new Thread(recovery::recoverAll, "telemetry-recovery");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void accept(TelemetryEvent event) {
        queue.offer(event);
    }

    public synchronized ExportOutcome exportNow() {
        if (session == null) {
            return notRunning();
        }

        session.writer().flushNow();

        return finaliser.finalise(session.logFile(), clock.instant(), false);
    }

    public synchronized ExportOutcome end() {
        if (endOutcome != null) {
            return endOutcome;
        }

        if (session == null) {
            return notRunning();
        }

        Session ending = session;
        session = null;
        sampler.stop();
        ending.writer().stopAndAwait(WRITER_STOP_MILLIS);

        ExportOutcome outcome = finaliser.finaliseAsTask(ending.logFile(), clock.instant(), false);
        if (outcome.success()) {
            deleteLog(ending.logFile());
        }

        endOutcome = outcome;

        return outcome;
    }

    private static void deleteLog(Path logFile) {
        try {
            Files.deleteIfExists(logFile);
        }

        catch (IOException ex) {
        }
    }

    private static ExportOutcome notRunning() {
        return new ExportOutcome(List.of(), List.of("No telemetry session is running."));
    }
}