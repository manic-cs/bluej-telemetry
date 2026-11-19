package edu.uwi.telemetry.export;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import edu.uwi.telemetry.io.FileSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests session completion notifications and the collection
 * of export results from subscribed exporters.
 *
 * @author Sawdah Kadir
 */
class SessionCompletionPublisherTest {
    private Path tempDir;

    @BeforeEach
    void createTempDir() throws IOException {
        tempDir = Files.createTempDirectory("publisher-test");
    }

    @AfterEach
    void deleteTempDir() {
        FileSupport.deleteTree(tempDir);
    }

    @Test
    void notifiesEverySubscriberAndCollectsFiles() {
        SessionCompletionPublisher publisher = new SessionCompletionPublisher();
        publisher.subscribe(new JsonSessionExporter(tempDir));
        List<SessionReport> seen = new ArrayList<>();
        publisher.subscribe(report -> {
            seen.add(report);
            return tempDir.resolve("custom.txt");
        });

        ExportOutcome result = publisher.publish(JsonSessionExporterTest.sampleReport());

        assertEquals(2, publisher.subscriberCount());
        assertTrue(result.problems().isEmpty());
        assertTrue(result.success());
        assertEquals(2, result.files().size());
        assertEquals(1, seen.size());
        assertTrue(Files.exists(tempDir.resolve("session-S20261101-100000-abc123.json")));
    }

    @Test
    void oneFailingSubscriberDoesNotStopTheOthers() {
        SessionCompletionPublisher publisher = new SessionCompletionPublisher();
        publisher.subscribe(new SessionExporter() {
            @Override
            public String formatName() {
                return "PDF";
            }

            @Override
            public Path export(SessionReport report) throws IOException {
                throw new IOException("disk full");
            }
        });

        publisher.subscribe(new JsonSessionExporter(tempDir));

        ExportOutcome result = publisher.publish(JsonSessionExporterTest.sampleReport());

        assertFalse(result.success());
        assertEquals(1, result.files().size());
        assertEquals(1, result.problems().size());
        assertTrue(result.problems().get(0).contains("PDF"));
        assertTrue(result.problems().get(0).contains("disk full"));
    }

    @Test
    void outcomeDescribesFilesAndProblems() {
        ExportOutcome outcome = new ExportOutcome(List.of(Path.of("a.json")), List.of("PDF export failed: x"));
        assertFalse(outcome.success());
        assertTrue(outcome.describe().contains("a.json"));
        assertTrue(outcome.describe().contains("PDF export failed: x"));
    }
}