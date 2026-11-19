package edu.uwi.telemetry.export;

import edu.uwi.telemetry.analysis.ClassTimelineEntry;
import edu.uwi.telemetry.analysis.Formats;
import edu.uwi.telemetry.analysis.SessionSummary;
import edu.uwi.telemetry.export.PdfCanvas.Rgb;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates two-page PDF reports containing BlueJ session statistics.
 * 
 * @author Sawdah Kadir
 */
public final class PdfReportExporter extends FileSessionExporter {
    static final float MARGIN = 40f;
    static final float ROW_HEIGHT = 18f;
    static final int MAX_ERROR_ROWS = 6;
    static final int MAX_CODE_ROWS = 3;
    static final int MAX_EXCEPTION_ROWS = 2;
    static final int MAX_BAR_ROWS = 6;
    static final int MAX_TIMELINE_ROWS = 14;

    public PdfReportExporter(Path outputDirectory) {
        super(outputDirectory);
    }

    @Override
    public String formatName() {
        return "PDF";
    }

    @Override
    protected String fileExtension() {
        return "pdf";
    }

    @Override
    protected void writeTo(SessionReport report, Path file) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDPage first = new PDPage(PDRectangle.A4);
            PDPage second = new PDPage(PDRectangle.A4);

            document.addPage(first);
            document.addPage(second);

            try (PdfCanvas canvas = new PdfCanvas(document, first, regular, bold)) {
                renderFirstPage(canvas, report.summary());
            }

            try (PdfCanvas canvas = new PdfCanvas(document, second, regular, bold)) {
                renderSecondPage(canvas, report.summary());
            }

            document.save(file.toFile());
        }
    }

    private void renderFirstPage(PdfCanvas c, SessionSummary s) throws IOException {
        drawHeader(c, s);
        drawTimeSection(c, s);
        drawCompilationSection(c, s);
        drawExecutionSection(c, s);
        drawErrorCodeSection(c, s);
        drawFooter(c);
    }

    private void drawHeader(PdfCanvas c, SessionSummary s) throws IOException {
        float y = 800;
        c.text(MARGIN, y, true, 20, "BlueJ Session Report");
        c.text(MARGIN, y - 25, false, 10, "Session ID: " + s.sessionId());
        c.text(MARGIN, y - 39, false, 10, "Student ID: " + s.studentId());
        c.text(MARGIN, y - 53, false, 10, "Start: " + seconds(s.start()) + "     End: " + seconds(s.end()));
        c.text(MARGIN, y - 67, false, 10, "Status: " + (s.interrupted() ? "Interrupted (recovered from an unfinished session)" : "Completed"));
        c.text(MARGIN, y - 84, false, 10, "Projects: opened " + s.projectsOpened() + ", created " + s.projectsCreated() + ", deleted " + s.projectsDeleted());
        c.text(MARGIN, y - 98, false, 10, "Classes: created " + s.classesCreated() + ", deleted " + s.classesDeleted() + ", edited " + s.classesModified());
    }

    private void drawTimeSection(PdfCanvas c, SessionSummary s) throws IOException {
        float y = 665;
        c.text(MARGIN, y, true, 13, "Active vs idle time");
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Measure", "Value"});
        rows.add(new String[]{"Session length", Formats.duration(s.sessionLength().getSeconds())});
        rows.add(new String[]{"Active time", Formats.duration(s.activeTime().getSeconds())});
        rows.add(new String[]{"Idle time", Formats.duration(s.idleTime().getSeconds())});
        rows.add(new String[]{"Active share", Formats.percent(s.activeRatio())});
        rows.add(new String[]{"Characters typed", String.valueOf(s.charactersTyped())});
        rows.add(new String[]{"Characters deleted", String.valueOf(s.charactersDeleted())});
        rows.add(new String[]{"Typing speed (chars/second)", String.format("%.1f", s.typingSpeedCharsPerSecond())});
        drawTable(c, y - 10, new float[]{170, 100}, rows);
        drawPie(c, 450, 605, 50, s);
    }

    private void drawCompilationSection(PdfCanvas c, SessionSummary s) throws IOException {
        float y = 485;
        c.text(MARGIN, y, true, 13, "Compilation summary");
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Measure", "Value"});
        rows.add(new String[]{"Compilation attempts", String.valueOf(s.totalCompilations())});
        rows.add(new String[]{"Successful", String.valueOf(s.compilationsSucceeded())});
        rows.add(new String[]{"Failed", String.valueOf(s.compilationsFailed())});
        rows.add(new String[]{"Success rate", Formats.percent(s.compileSuccessRate())});
        rows.add(new String[]{"Avg time between compilations", s.secondsBetweenCompilations().isEmpty() ? "n/a" : Formats.duration(Math.round(s.averageSecondsBetweenCompilations()))});
        drawTable(c, y - 10, new float[]{200, 120}, rows);
    }

    private void drawExecutionSection(PdfCanvas c, SessionSummary s) throws IOException {
        float y = 350;
        c.text(MARGIN, y, true, 13, "Program executions");
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Measure", "Value"});
        rows.add(new String[]{"Total runs", String.valueOf(s.totalExecutions())});
        rows.add(new String[]{"Finished normally", String.valueOf(s.executionsNormal())});
        rows.add(new String[]{"Ended with a runtime exception", String.valueOf(s.executionsWithException())});
        rows.add(new String[]{"Stopped by the student", String.valueOf(s.executionsTerminated())});

        int exceptionRows = 0;
        int hiddenTypes = 0;
        int hiddenCount = 0;

        for (Map.Entry<String, Integer> e : s.exceptionFrequency().entrySet()) {
            if (exceptionRows < MAX_EXCEPTION_ROWS) {
                rows.add(new String[]{"  " + simpleName(e.getKey()), String.valueOf(e.getValue())});
                exceptionRows++;
            }

            else {
                hiddenTypes++;
                hiddenCount += e.getValue();
            }
        }

        if (hiddenTypes > 0) {
            rows.add(new String[]{"  Other exceptions (" + hiddenTypes + " types)", String.valueOf(hiddenCount)});
        }

        drawTable(c, y - 10, new float[]{250, 70}, rows);
    }

    private void drawErrorCodeSection(PdfCanvas c, SessionSummary s) throws IOException {
        float y = 175;
        c.text(MARGIN, y, true, 13, "Compiler error codes (javac)");

        if (s.errorCodeFrequency().isEmpty()) {
            c.text(MARGIN, y - 18, false, 10, "No javac error codes were captured in this session.");
            return;
        }

        List<String[]> rows = countRows(new String[]{"javac error code", "Count"}, s.errorCodeFrequency(), MAX_CODE_ROWS, "Other codes (%d more)", false);
        drawTable(c, y - 10, new float[]{300, 70}, rows);
    }

    private void drawPie(PdfCanvas c, float cx, float cy, float radius, SessionSummary s) throws IOException {
        double active = s.activeTime().toMillis();
        double total = active + s.idleTime().toMillis();

        if (total <= 0) {
            c.wedge(cx, cy, radius, 0, 2 * Math.PI, Rgb.EMPTY);
        }

        else {
            double activeSweep = 2 * Math.PI * active / total;
            if (activeSweep > 0) {
                c.wedge(cx, cy, radius, 0, activeSweep, Rgb.ACTIVE);
            }

            if (2 * Math.PI - activeSweep > 0) {
                c.wedge(cx, cy, radius, activeSweep, 2 * Math.PI - activeSweep, Rgb.IDLE);
            }
        }

        float legendX = cx - radius;
        float legendY = cy - radius - 18;

        c.fillRect(legendX, legendY - 1, 9, 9, Rgb.ACTIVE);
        c.text(legendX + 14, legendY, false, 9, "Active " + Formats.duration(s.activeTime().getSeconds()));
        c.fillRect(legendX, legendY - 15, 9, 9, Rgb.IDLE);
        c.text(legendX + 14, legendY - 14, false, 9, "Idle " + Formats.duration(s.idleTime().getSeconds()));
    }

    private void renderSecondPage(PdfCanvas c, SessionSummary s) throws IOException {
        c.text(MARGIN, 800, true, 16, "Errors and class progression");
        drawErrorTable(c, s);
        drawErrorBarChart(c, s.errorFrequency());
        drawTimeline(c, s);
        drawFooter(c);
    }

    private void drawErrorTable(PdfCanvas c, SessionSummary s) throws IOException {
        float y = 770;
        c.text(MARGIN, y, true, 13, "Compilation error frequency");

        if (s.errorFrequency().isEmpty()) {
            c.text(MARGIN, y - 20, false, 10, "No compilation errors were recorded in this session.");

            return;
        }

        List<String[]> rows = countRows(new String[]{"Error type", "Count", "Share"}, s.errorFrequency(), MAX_ERROR_ROWS, "Other (%d more types)", true);

        drawTable(c, y - 10, new float[]{230, 70, 80}, rows);
    }

    private void drawErrorBarChart(PdfCanvas c, Map<String, Integer> errors) throws IOException {
        float yTop = 570;
        c.text(MARGIN, 590, true, 13, "Error types (bar chart)");

        if (errors.isEmpty()) {
            c.text(MARGIN, yTop - 10, false, 10, "Nothing to chart.");
            return;
        }

        int max = errors.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        float labelWidth = 170f;
        float maxBar = 250f;
        float y = yTop;
        int shown = 0;

        for (Map.Entry<String, Integer> e : errors.entrySet()) {
            if (shown++ >= MAX_BAR_ROWS) {
                break;
            }

            c.text(MARGIN, y - 10, false, 9, c.fit(e.getKey(), false, 9, labelWidth - 6));

            float width = Math.max(2f, maxBar * e.getValue() / max);

            c.fillRect(MARGIN + labelWidth, y - 12, width, 12, Rgb.BAR);
            c.text(MARGIN + labelWidth + width + 5, y - 10, false, 9, String.valueOf(e.getValue()));
            y -= 20;
        }

        c.line(MARGIN + labelWidth, yTop + 2, MARGIN + labelWidth, y + 6);
    }

    private void drawTimeline(PdfCanvas c, SessionSummary s) throws IOException {
        float y = 440;

        c.text(MARGIN, y, true, 13, "Class creation and method count timeline");
        List<ClassTimelineEntry> timeline = s.classTimeline();

        if (timeline.isEmpty()) {
            c.text(MARGIN, y - 20, false, 10, "No class creations or method changes were recorded.");
            return;
        }

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Time into session", "Class", "Change", "Methods"});

        int limit = Math.min(timeline.size(), MAX_TIMELINE_ROWS);

        for (int i = 0; i < limit; i++) {
            ClassTimelineEntry t = timeline.get(i);
            rows.add(new String[]{Formats.clock(t.offsetSeconds()), t.className(), t.change(), t.methodCount() < 0 ? "-" : String.valueOf(t.methodCount())});
        }
        if (timeline.size() > limit) {
            rows.add(new String[]{"...", (timeline.size() - limit) + " more entries (see JSON)", "", ""});
        }

        drawTable(c, y - 10, new float[]{110, 200, 80, 70}, rows);
    }

    private static List<String[]> countRows(String[] header, Map<String, Integer> counts, int maxRows, String otherLabel, boolean withShare) {
        List<String[]> rows = new ArrayList<>();
        rows.add(header);

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        int shown = 0;
        int hiddenTypes = 0;
        int hiddenCount = 0;

        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (shown < maxRows) {
                rows.add(countRow(e.getKey(), e.getValue(), total, withShare));
                shown++;
            }

            else {
                hiddenTypes++;
                hiddenCount += e.getValue();
            }
        }

        if (hiddenTypes > 0) {
            rows.add(countRow(String.format(otherLabel, hiddenTypes), hiddenCount, total, withShare));
        }

        return rows;
    }

    private static String[] countRow(String label, int count, int total, boolean withShare) {
        return withShare ? new String[]{label, String.valueOf(count), Formats.percent((double) count / total)} : new String[]{label, String.valueOf(count)};
    }

    private void drawTable(PdfCanvas c, float yTop, float[] widths, List<String[]> rows) throws IOException {
        float total = 0;
        for (float w : widths) {
            total += w;
        }

        float y = yTop;

        for (int r = 0; r < rows.size(); r++) {
            boolean header = r == 0;
            if (header) {
                c.fillRect(MARGIN, y - ROW_HEIGHT, total, ROW_HEIGHT, Rgb.HEADER_FILL);
            }

            float cellX = MARGIN;
            String[] row = rows.get(r);

            for (int col = 0; col < widths.length; col++) {
                c.strokeRect(cellX, y - ROW_HEIGHT, widths[col], ROW_HEIGHT);
                String value = col < row.length ? row[col] : "";
                c.text(cellX + 5, y - ROW_HEIGHT + 5.5f, header, 9.5f,
                        c.fit(value, header, 9.5f, widths[col] - 10));
                cellX += widths[col];
            }

            y -= ROW_HEIGHT;
        }
    }

    private void drawFooter(PdfCanvas c) throws IOException {
        c.line(MARGIN, 58, 555, 58);
        c.text(MARGIN, 45, false, 8, "Generated by the BlueJ Telemetry plugin. Source code, individual keystrokes and " + "activity outside BlueJ are never collected.");
        c.text(MARGIN, 34, false, 8, "Only compilations started by the student are counted; BlueJ background checks are ignored.");
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private static String seconds(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS) + " (UTC)";
    }
}