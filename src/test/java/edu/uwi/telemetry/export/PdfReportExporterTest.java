package edu.uwi.telemetry.export;

import edu.uwi.telemetry.MutableClock;
import edu.uwi.telemetry.analysis.SessionAnalyzer;
import edu.uwi.telemetry.event.TelemetryEventFactory;
import edu.uwi.telemetry.io.FileSupport;
import edu.uwi.telemetry.model.SessionData;
import edu.uwi.telemetry.model.TelemetryEvent;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests PDF session report generation, content and page layout.
 *
 * @author Sawdah Kadir
 */
class PdfReportExporterTest {
    private Path tempDir;

    @BeforeEach
    void createTempDir() throws IOException {
        tempDir = Files.createTempDirectory("pdf-exporter-test");
    }

    @AfterEach
    void deleteTempDir() {
        FileSupport.deleteTree(tempDir);
    }

    private static String textOf(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void producesExactlyTwoPages() throws IOException {
        Path pdf = new PdfReportExporter(tempDir).export(JsonSessionExporterTest.sampleReport());

        assertEquals("session-S20261101-100000-abc123.pdf", pdf.getFileName().toString());
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            assertEquals(2, document.getNumberOfPages());
        }

        assertFalse(Files.exists(tempDir.resolve(pdf.getFileName() + ".tmp")));
    }

    @Test
    void containsTheTablesAndChartsHeadings() throws IOException {
        Path pdf = new PdfReportExporter(tempDir).export(JsonSessionExporterTest.sampleReport());
        String text = textOf(pdf);

        assertTrue(text.contains("BlueJ Session Report"));
        assertTrue(text.contains("Active vs idle time"));
        assertTrue(text.contains("Typing speed"));
        assertTrue(text.contains("Projects: opened"));
        assertTrue(text.contains("Compilation summary"));
        assertTrue(text.contains("Success rate"));
        assertTrue(text.contains("50.0%"));
        assertTrue(text.contains("Program executions"));
        assertTrue(text.contains("Compilation error frequency"));
        assertTrue(text.contains("Cannot find symbol"));
        assertTrue(text.contains("Error types (bar chart)"));
        assertTrue(text.contains("Circle"));
    }

    @Test
    void emptySessionStillProducesTwoPages() throws IOException {
        SessionData data = new SessionData("S-empty", "u", Instant.parse("2026-11-01T10:00:00Z"), null, true, List.of());
        SessionReport report = new SessionReport(data, new SessionAnalyzer(Duration.ofSeconds(60)).analyze(data));

        Path pdf = new PdfReportExporter(tempDir).export(report);

        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            assertEquals(2, document.getNumberOfPages());
        }

        String text = textOf(pdf);
        assertTrue(text.contains("No compilation errors were recorded"));
        assertTrue(text.contains("Interrupted"));
    }

    @Test
    void manyErrorTypesAndTimelineRowsAreTruncatedToFitThePage() throws IOException {
        Instant t0 = Instant.parse("2026-11-01T10:00:00Z");
        TelemetryEventFactory f = new TelemetryEventFactory(new MutableClock(t0));
        String[] messages = {"cannot find symbol", "';' expected", "incompatible types", "missing return statement", "might not have been initialized", "reached end of file while parsing", "unreported exception X", "is already defined", "unreachable statement", "has private access", "something odd"};
        List<TelemetryEvent> events = new ArrayList<>();

        for (int i = 0; i < messages.length; i++) {
            TelemetryEvent e = f.compileError("A.java", i, messages[i], true);
            events.add(new TelemetryEvent(t0.plusSeconds(i), e.type(), e.data()));
        }

        for (int i = 0; i < 30; i++) {
            TelemetryEvent e = f.classCreated("P", "Class" + i + "WithAVeryLongNameThatMustBeShortenedToFitTheCell");
            events.add(new TelemetryEvent(t0.plusSeconds(100 + i), e.type(), e.data()));
        }

        SessionData data = new SessionData("S-big", "u", t0, t0.plusSeconds(500), false, events);
        SessionReport report = new SessionReport(data, new SessionAnalyzer(Duration.ofSeconds(60)).analyze(data));

        Path pdf = new PdfReportExporter(tempDir).export(report);

        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            assertEquals(2, document.getNumberOfPages());
        }

        String text = textOf(pdf);
        assertTrue(text.contains("more types"));
        assertTrue(text.contains("more entries"));
    }

    @Test
    void nonAsciiTextDoesNotBreakTheReport() throws IOException {
        assertEquals("caf? ??", PdfCanvas.sanitize("caf\u00e9 \u4e2d\u6587"));
        Instant t0 = Instant.parse("2026-11-01T10:00:00Z");
        TelemetryEventFactory f = new TelemetryEventFactory(new MutableClock(t0));
        SessionData data = new SessionData("S-uni", "\u00e9tudiant", t0, t0.plusSeconds(10), false, List.of(f.classCreated("P", "Caf\u00e9")));
        SessionReport report = new SessionReport(data, new SessionAnalyzer(Duration.ofSeconds(60)).analyze(data));
        Path pdf = new PdfReportExporter(tempDir).export(report);
        assertTrue(Files.size(pdf) > 0);
    }

    @Test
    void listsJavacErrorCodesOnTheFirstPage() throws IOException {
        Instant t0 = Instant.parse("2026-11-01T10:00:00Z");
        TelemetryEventFactory f = new TelemetryEventFactory(new MutableClock(t0));
        List<TelemetryEvent> events = new ArrayList<>();
        String[] codes = {"compiler.err.expected", "compiler.err.cant.resolve.location", "compiler.err.prob.found.req", "compiler.err.missing.ret.stmt", "compiler.err.var.might.not.have.been.initialized", "compiler.err.unreported.exception.need.to.catch.or.throw", "compiler.err.already.defined"};

        for (int i = 0; i < codes.length; i++) {
            TelemetryEvent e = f.compileDiagnostic("A.java", i + 1, codes[i]);
            events.add(new TelemetryEvent(t0.plusSeconds(i), e.type(), e.data()));
        }

        SessionData data = new SessionData("S-codes", "u", t0, t0.plusSeconds(60), false, events);
        SessionReport report = new SessionReport(data, new SessionAnalyzer(Duration.ofSeconds(60)).analyze(data));

        Path pdf = new PdfReportExporter(tempDir).export(report);

        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            assertEquals(2, document.getNumberOfPages());
        }

        String text = textOf(pdf);
        assertTrue(text.contains("Compiler error codes (javac)"));
        assertTrue(text.contains("compiler.err.cant.resolve.location"));
        assertTrue(text.contains("Other codes"));
    }
}