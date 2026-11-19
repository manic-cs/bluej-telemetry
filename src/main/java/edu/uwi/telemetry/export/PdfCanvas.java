package edu.uwi.telemetry.export;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.IOException;

/**
 * Provides drawing and text operations for generating PDF reports.
 */
final class PdfCanvas implements AutoCloseable {
    record Rgb(float r, float g, float b) {
        static final Rgb BLACK = new Rgb(0f, 0f, 0f);
        static final Rgb LINE = new Rgb(0.6f, 0.6f, 0.6f);
        static final Rgb HEADER_FILL = new Rgb(0.88f, 0.91f, 0.95f);
        static final Rgb EMPTY = new Rgb(0.85f, 0.85f, 0.85f);
        static final Rgb ACTIVE = new Rgb(0.18f, 0.64f, 0.40f);
        static final Rgb IDLE = new Rgb(0.95f, 0.62f, 0.20f);
        static final Rgb BAR = new Rgb(0.25f, 0.45f, 0.80f);
    }

    /**
     * PDF content stream used for drawing page elements.
     */
    private final PDPageContentStream stream;
    /**
     * Regular font used for PDF text.
     */
    private final PDFont regular;
    /**
     * Bold font used for headings and table headers.
     */
    private final PDFont bold;

    PdfCanvas(PDDocument document, PDPage page, PDFont regular, PDFont bold) throws IOException {
        this.stream = new PDPageContentStream(document, page);
        this.regular = regular;
        this.bold = bold;
    }

    /**
     * Replaces unsupported characters with safe ASCII equivalents.
     */
    static String sanitize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 32 && c < 127) {
                sb.append(c);
            }

            else if (c == '\t' || c == '\n' || c == '\r') {
                sb.append(' ');
            }

            else {
                sb.append('?');
            }
        }

        return sb.toString();
    }

    /**
     * Calculates the rendered width of sanitized text using the
     * selected font and size.
     */
    float textWidth(String text, boolean useBold, float size) throws IOException {
        return (useBold ? bold : regular).getStringWidth(sanitize(text)) / 1000f * size;
    }

    /**
     * Shortens text with an ellipsis until it fits within the specified width.
     */
    String fit(String text, boolean useBold, float size, float maxWidth) throws IOException {
        String clean = sanitize(text);
        if (textWidth(clean, useBold, size) <= maxWidth) {
            return clean;
        }

        String ellipsis = "...";
        int end = clean.length();

        while (end > 0 && textWidth(clean.substring(0, end) + ellipsis, useBold, size) > maxWidth) {
            end--;
        }

        return clean.substring(0, end) + ellipsis;
    }

    /**
     * Draws sanitized text at the specified page coordinates.
     */
    void text(float x, float y, boolean useBold, float size, String text) throws IOException {
        fillColor(Rgb.BLACK);
        stream.beginText();
        stream.setFont(useBold ? bold : regular, size);
        stream.newLineAtOffset(x, y);
        stream.showText(sanitize(text));
        stream.endText();
    }

    /**
     * Draws a filled rectangle using the specified colour.
     */
    void fillRect(float x, float y, float width, float height, Rgb color) throws IOException {
        fillColor(color);
        stream.addRect(x, y, width, height);
        stream.fill();
    }

    /**
     * Draws the outline of a rectangle using the configured line style.
     */
    void strokeRect(float x, float y, float width, float height) throws IOException {
        lineStyle();
        stream.addRect(x, y, width, height);
        stream.stroke();
    }

    /**
     * Draws a line between two page coordinates.
     */
    void line(float x1, float y1, float x2, float y2) throws IOException {
        lineStyle();
        stream.moveTo(x1, y1);
        stream.lineTo(x2, y2);
        stream.stroke();
    }

    /**
     * Draws a filled pie-chart segment using a series of line segments.
     */
    void wedge(float cx, float cy, float radius, double startAngle, double sweep, Rgb color) throws IOException {
        fillColor(color);
        stream.moveTo(cx, cy);
        int steps = Math.max(2, (int) Math.ceil(Math.abs(sweep) / (Math.PI / 36)));

        for (int i = 0; i <= steps; i++) {
            double angle = startAngle + sweep * i / steps;
            stream.lineTo(cx + (float) (radius * Math.cos(angle)), cy + (float) (radius * Math.sin(angle)));
        }

        stream.closePath();
        stream.fill();
    }

    /**
     * Sets the content stream's non-stroking colour.
     */
    private void fillColor(Rgb color) throws IOException {
        stream.setNonStrokingColor(color.r(), color.g(), color.b());
    }

    /**
     * Sets the configured grey stroke colour and line width.
     */
    private void lineStyle() throws IOException {
        stream.setStrokingColor(Rgb.LINE.r(), Rgb.LINE.g(), Rgb.LINE.b());
        stream.setLineWidth(0.5f);
    }

    /**
     * Closes the underlying PDF content stream.
     */
    @Override
    public void close() throws IOException {
        stream.close();
    }
}