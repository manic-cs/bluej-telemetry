package edu.uwi.telemetry.log;

import edu.uwi.telemetry.model.SessionHeader;
import edu.uwi.telemetry.model.TelemetryEvent;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Manages the storage of telemetry events and session headers in a log file.
 * Creates the required directories and writes information in JSON Lines
 * format using UTF-8 encoding. Supports appending events, flushing
 * buffered data, and closing the file writer.
 *
 * @author Micah Mitchell
 */

public final class EventLog implements Closeable {
    private final Path file;
    private final BufferedWriter writer;

    public EventLog(Path file) throws IOException {
        this.file = file;
        Path parent = file.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        this.writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public Path file() {
        return file;
    }

    public void writeHeader(SessionHeader header) throws IOException {
        writer.write(EventCodec.encodeHeader(header));
        writer.newLine();
        writer.flush();
    }

    public void append(TelemetryEvent event) throws IOException {
        writer.write(EventCodec.encodeEvent(event));
        writer.newLine();
    }

    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}