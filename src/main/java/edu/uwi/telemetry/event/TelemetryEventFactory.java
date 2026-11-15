package edu.uwi.telemetry.event;

import edu.uwi.telemetry.model.EditSummary;
import edu.uwi.telemetry.model.EditorSample;
import edu.uwi.telemetry.model.EventType;
import edu.uwi.telemetry.model.ExecutionOutcome;
import edu.uwi.telemetry.model.TelemetryEvent;
import edu.uwi.telemetry.model.TelemetryEvent.Key;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Creates structured telemetry events for activities within a BlueJ session.
 * Records project and class changes, editor activity, compilation results,
 * and program execution outcomes using timestamps from a configurable clock.
 * Also handles compiler error classification, message length limits,
 * and default values for missing event information.
 *
 * @author Micah Mitchell
 */

public final class TelemetryEventFactory {
    static final int MAX_MESSAGE_LENGTH = 300;

    private final Clock clock;

    public TelemetryEventFactory(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public TelemetryEvent projectOpened(String project, int classCount) {
        return create(EventType.PROJECT_OPENED, Key.PROJECT, project, Key.CLASS_COUNT, classCount);
    }

    public TelemetryEvent projectCreated(String project) {
        return create(EventType.PROJECT_CREATED, Key.PROJECT, project);
    }

    public TelemetryEvent projectClosed(String project) {
        return create(EventType.PROJECT_CLOSED, Key.PROJECT, project);
    }

    public TelemetryEvent projectDeleted(String project) {
        return create(EventType.PROJECT_DELETED, Key.PROJECT, project);
    }

    public TelemetryEvent classCreated(String project, String className) {
        return classEvent(EventType.CLASS_CREATED, project, className);
    }

    public TelemetryEvent classDeleted(String project, String className) {
        return classEvent(EventType.CLASS_DELETED, project, className);
    }

    public TelemetryEvent classModified(String project, String className) {
        return classEvent(EventType.CLASS_MODIFIED, project, className);
    }

    public TelemetryEvent methodCountChanged(String project, String className, int methodCount) {
        return create(EventType.METHOD_COUNT_CHANGED, Key.PROJECT, project, Key.CLASS_NAME, className, Key.METHOD_COUNT, methodCount);
    }

    public TelemetryEvent editorSample(EditorSample sample) {
        return create(EventType.EDITOR_SAMPLE, Key.PROJECT, sample.project(), Key.CLASS_NAME, sample.className(), Key.LINE, sample.line(), Key.COLUMN, sample.column(), Key.LINE_COUNT, sample.lineCount());
    }

    public TelemetryEvent editorEdit(String project, String className, EditSummary summary) {
        return create(EventType.EDITOR_EDIT, Key.PROJECT, project, Key.CLASS_NAME, className, Key.EDITS, summary.edits(), Key.TYPED_CHARS, summary.typedChars(), Key.BULK_CHARS, summary.bulkChars(), Key.DELETED_CHARS, summary.deletedChars());
    }

    public TelemetryEvent compileStarted(int fileCount, boolean userGenerated) {
        return compileEvent(EventType.COMPILE_STARTED, fileCount, userGenerated);
    }

    public TelemetryEvent compileSucceeded(int fileCount, boolean userGenerated) {
        return compileEvent(EventType.COMPILE_SUCCEEDED, fileCount, userGenerated);
    }

    public TelemetryEvent compileFailed(int fileCount, boolean userGenerated) {
        return compileEvent(EventType.COMPILE_FAILED, fileCount, userGenerated);
    }

    public TelemetryEvent compileError(String file, int line, String message, boolean userGenerated) {
        return diagnostic(EventType.COMPILE_ERROR, file, line, message, userGenerated);
    }

    public TelemetryEvent compileWarning(String file, int line, String message, boolean userGenerated) {
        return diagnostic(EventType.COMPILE_WARNING, file, line, message, userGenerated);
    }

    public TelemetryEvent compileDiagnostic(String file, int line, String code) {
        return create(EventType.COMPILE_DIAGNOSTIC, Key.FILE, file == null ? "unknown" : file, Key.LINE, line, Key.CODE, code == null ? "unknown" : code, Key.USER_GENERATED, true);
    }

    public TelemetryEvent executionFinished(String className, String methodName, ExecutionOutcome outcome, boolean hasResult) {
        return create(EventType.EXECUTION_FINISHED, Key.CLASS_NAME, className, Key.METHOD, methodName == null ? "<constructor>" : methodName, Key.OUTCOME, outcome.name(), Key.HAS_RESULT, hasResult);
    }

    public TelemetryEvent runtimeException(String className, String methodName, String exceptionClass, String message, String stack) {
        String text = message == null ? "" : message.strip();
        if (text.length() > MAX_MESSAGE_LENGTH) {
            text = text.substring(0, MAX_MESSAGE_LENGTH);
        }

        return create(EventType.EXECUTION_EXCEPTION, Key.CLASS_NAME, className == null ? "unknown" : className, Key.METHOD, methodName == null ? "<constructor>" : methodName, Key.EXCEPTION, exceptionClass == null ? "unknown" : exceptionClass, Key.MESSAGE, text, Key.STACK, stack == null ? "" : stack);
    }

    private TelemetryEvent classEvent(EventType type, String project, String className) {
        return create(type, Key.PROJECT, project, Key.CLASS_NAME, className);
    }

    private TelemetryEvent compileEvent(EventType type, int fileCount, boolean userGenerated) {
        return create(type, Key.FILES, fileCount, Key.USER_GENERATED, userGenerated);
    }

    private TelemetryEvent diagnostic(EventType type, String file, int line, String message, boolean userGenerated) {
        String text = message == null ? "" : message.strip();
        if (text.length() > MAX_MESSAGE_LENGTH) {
            text = text.substring(0, MAX_MESSAGE_LENGTH);
        }

        return create(type, Key.FILE, file == null ? "unknown" : file, Key.LINE, line, Key.MESSAGE, text, Key.CATEGORY, JavacErrorClassifier.classify(text), Key.USER_GENERATED, userGenerated);
    }

    private TelemetryEvent create(EventType type, Object... keyValues) {
        Map<String, Object> data = new LinkedHashMap<>();

        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            data.put((String) keyValues[i], keyValues[i + 1]);
        }

        return new TelemetryEvent(clock.instant(), type, data);
    }
}