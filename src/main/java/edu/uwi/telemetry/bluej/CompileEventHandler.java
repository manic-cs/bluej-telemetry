package edu.uwi.telemetry.bluej;

/**
 * Adapter between BlueJ's {@link CompileListener} and the plugin's {@link EventSink}.
 * Turns compile started, succeeded, failed, error and warning events into telemetry events through the
 * {@link TelemetryEventFactory} and queues them; it only builds and hands over events and never blocks.
 * It records whether BlueJ marked the compilation as started by the student so that BlueJ's automatic
 * background checks are not counted as attempts. An optional {@link CompileFailureListener} is told which
 * files failed so that javac error codes can be collected on a background thread.
 * @author Rinard Guy
 */

import bluej.extensions2.event.CompileEvent;
import bluej.extensions2.event.CompileListener;
import edu.uwi.telemetry.diagnostics.CompileFailureListener;
import edu.uwi.telemetry.event.EventSink;
import edu.uwi.telemetry.event.TelemetryEventFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CompileEventHandler implements CompileListener {
    private final EventSink sink;
    private final TelemetryEventFactory factory;
    private final CompileFailureListener failureListener;

    public CompileEventHandler(EventSink sink, TelemetryEventFactory factory) {
        this(sink, factory, null);
    }

    /** The optional failure listener is told about failed compilations (it works on its own thread). */
    public CompileEventHandler(EventSink sink, TelemetryEventFactory factory, CompileFailureListener failureListener) {
        this.sink = sink;
        this.factory = factory;
        this.failureListener = failureListener;
    }
    
    @Override
    public void compileStarted(CompileEvent event) {
        Safe.run(() -> sink.accept(factory.compileStarted(fileCount(event), event.isUserGeneratedCompilation())));
    }

    @Override
    public void compileError(CompileEvent event) {
        Safe.run(() -> sink.accept(factory.compileError(fileName(event), event.getErrorLineNumber(), event.getErrorMessage(), event.isUserGeneratedCompilation())));
    }

    @Override
    public void compileWarning(CompileEvent event) {
        Safe.run(() -> sink.accept(factory.compileWarning(fileName(event), event.getErrorLineNumber(), event.getErrorMessage(), event.isUserGeneratedCompilation())));
    }

    @Override
    public void compileSucceeded(CompileEvent event) {
        Safe.run(() -> sink.accept(factory.compileSucceeded(fileCount(event), event.isUserGeneratedCompilation())));
    }

    @Override
    public void compileFailed(CompileEvent event) {
        Safe.run(() -> {
            sink.accept(factory.compileFailed(fileCount(event), event.isUserGeneratedCompilation()));
            if (failureListener != null) {
                failureListener.compileFailed(paths(event), event.isUserGeneratedCompilation());
            }
        });
    }

    private static List<Path> paths(CompileEvent event) {
        List<Path> paths = new ArrayList<>();
        File[] files = event.getFiles();
        if (files != null) {
            for (File file : files) {
                paths.add(file.toPath());
            }
        }

        return paths;
    }

    private static int fileCount(CompileEvent event) {
        File[] files = event.getFiles();
        return files == null ? 0 : files.length;
    }

    private static String fileName(CompileEvent event) {
        File[] files = event.getFiles();
        return files == null || files.length == 0 ? "unknown" : files[0].getName();
    }
}