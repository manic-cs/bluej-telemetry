package edu.uwi.telemetry.analysis;

import edu.uwi.telemetry.model.ExecutionOutcome;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores the statistical results of an analysed telemetry session
 * 
 * @author Sawdah Kadir
 */

public final class SessionSummary {
    private final String sessionId;
    private final String studentId;
    private final Instant start;
    private final Instant end;
    private final boolean interrupted;
    private final Duration sessionLength;
    private final Duration activeTime;
    private final Duration idleTime;
    private final int compilationsSucceeded;
    private final int compilationsFailed;
    private final int executionsNormal;
    private final int executionsWithException;
    private final int executionsTerminated;
    private final int executionsUnknown;
    private final Map<String, Integer> errorFrequency;
    private final List<Long> secondsBetweenCompilations;
    private final List<ClassTimelineEntry> classTimeline;
    private final int editorChanges;
    private final int textEdits;
    private final int charactersTyped;
    private final int charactersBulkInserted;
    private final int charactersDeleted;
    private final int typingSeconds;
    private final Map<String, Integer> exceptionFrequency;
    private final int classesCreated;
    private final int classesDeleted;
    private final int classesModified;
    private final Map<String, Integer> errorCodeFrequency;
    private final int projectsOpened;
    private final int projectsCreated;
    private final int projectsDeleted;

    private SessionSummary(Builder b) {
        this.sessionId = b.sessionId;
        this.studentId = b.studentId;
        this.start = b.start;
        this.end = b.end;
        this.interrupted = b.interrupted;
        this.sessionLength = b.sessionLength;
        this.activeTime = b.activeTime;
        this.idleTime = b.idleTime;
        this.compilationsSucceeded = b.compilationsSucceeded;
        this.compilationsFailed = b.compilationsFailed;
        this.executionsNormal = b.executionsNormal;
        this.executionsWithException = b.executionsWithException;
        this.executionsTerminated = b.executionsTerminated;
        this.executionsUnknown = b.executionsUnknown;
        this.errorFrequency = Collections.unmodifiableMap(new LinkedHashMap<>(b.errorFrequency));
        this.secondsBetweenCompilations = List.copyOf(b.secondsBetweenCompilations);
        this.classTimeline = List.copyOf(b.classTimeline);
        this.editorChanges = b.editorChanges;
        this.textEdits = b.textEdits;
        this.charactersTyped = b.charactersTyped;
        this.charactersBulkInserted = b.charactersBulkInserted;
        this.charactersDeleted = b.charactersDeleted;
        this.typingSeconds = b.typingSeconds;
        this.exceptionFrequency = Collections.unmodifiableMap(new LinkedHashMap<>(b.exceptionFrequency));
        this.classesCreated = b.classesCreated;
        this.classesDeleted = b.classesDeleted;
        this.classesModified = b.classesModified;
        this.errorCodeFrequency = Collections.unmodifiableMap(new LinkedHashMap<>(b.errorCodeFrequency));
        this.projectsOpened = b.projectsOpened;
        this.projectsCreated = b.projectsCreated;
        this.projectsDeleted = b.projectsDeleted;
    }

    public String sessionId() { return sessionId; }

    public String studentId() { return studentId; }

    public Instant start() { return start; }

    public Instant end() { return end; }

    public boolean interrupted() { return interrupted; }

    public Duration sessionLength() { return sessionLength; }

    public Duration activeTime() { return activeTime; }

    public Duration idleTime() { return idleTime; }

    public int compilationsSucceeded() { return compilationsSucceeded; }

    public int compilationsFailed() { return compilationsFailed; }

    public int executionsNormal() { return executionsNormal; }

    public int executionsWithException() { return executionsWithException; }

    public int executionsTerminated() { return executionsTerminated; }

    public int executionsUnknown() { return executionsUnknown; }

    public Map<String, Integer> errorFrequency() { return errorFrequency; }

    public List<Long> secondsBetweenCompilations() { return secondsBetweenCompilations; }

    public List<ClassTimelineEntry> classTimeline() { return classTimeline; }

    public int editorChanges() { return editorChanges; }

    public int textEdits() { return textEdits; }

    public int charactersTyped() { return charactersTyped; }

    public int charactersBulkInserted() { return charactersBulkInserted; }

    public int charactersDeleted() { return charactersDeleted; }

    public int typingSeconds() { return typingSeconds; }

    public Map<String, Integer> exceptionFrequency() { return exceptionFrequency; }

    public int classesCreated() { return classesCreated; }

    public int classesDeleted() { return classesDeleted; }

    public int classesModified() { return classesModified; }

    public Map<String, Integer> errorCodeFrequency() { return errorCodeFrequency; }

    public int projectsOpened() { return projectsOpened; }


    public int projectsCreated() { return projectsCreated; }

    public int projectsDeleted() { return projectsDeleted; }

    public int totalCompilations() {
        return compilationsSucceeded + compilationsFailed;
    }

    public double compileSuccessRate() {
        int total = totalCompilations();
        return total == 0 ? 0.0 : (double) compilationsSucceeded / total;
    }

    public int totalExecutions() {
        return executionsNormal + executionsWithException + executionsTerminated + executionsUnknown;
    }

    public int totalErrors() {
        return errorFrequency.values().stream().mapToInt(Integer::intValue).sum();
    }

    public double averageSecondsBetweenCompilations() {
        if (secondsBetweenCompilations.isEmpty()) {
            return 0.0;
        }

        return secondsBetweenCompilations.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    public double activeRatio() {
        long total = sessionLength.toMillis();
        return total <= 0 ? 0.0 : Math.min(1.0, (double) activeTime.toMillis() / total);
    }

    public double editsPerActiveMinute() {
        return perActiveMinute(textEdits);
    }

    public double charactersTypedPerActiveMinute() {
        return perActiveMinute(charactersTyped);
    }

    public double typingSpeedCharsPerSecond() {
        return typingSeconds <= 0 ? 0.0 : (double) charactersTyped / typingSeconds;
    }

    private double perActiveMinute(int count) {
        double minutes = activeTime.toMillis() / 60000.0;
        return minutes <= 0 ? 0.0 : count / minutes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String sessionId = "";
        private String studentId = "";
        private Instant start = Instant.EPOCH;
        private Instant end = Instant.EPOCH;
        private boolean interrupted;
        private Duration sessionLength = Duration.ZERO;
        private Duration activeTime = Duration.ZERO;
        private Duration idleTime = Duration.ZERO;
        private int compilationsSucceeded;
        private int compilationsFailed;
        private int executionsNormal;
        private int executionsWithException;
        private int executionsTerminated;
        private int executionsUnknown;
        private final Map<String, Integer> errorFrequency = new LinkedHashMap<>();
        private final List<Long> secondsBetweenCompilations = new ArrayList<>();
        private final List<ClassTimelineEntry> classTimeline = new ArrayList<>();
        private int editorChanges;
        private int textEdits;
        private int charactersTyped;
        private int charactersBulkInserted;
        private int charactersDeleted;
        private int typingSeconds;
        private final Map<String, Integer> exceptionFrequency = new LinkedHashMap<>();
        private int classesCreated;
        private int classesDeleted;
        private int classesModified;
        private final Map<String, Integer> errorCodeFrequency = new LinkedHashMap<>();
        private int projectsOpened;
        private int projectsCreated;
        private int projectsDeleted;

        public Builder session(String sessionId, String studentId, Instant start, Instant end, boolean interrupted) {
            this.sessionId = sessionId;
            this.studentId = studentId;
            this.start = start;
            this.end = end;
            this.interrupted = interrupted;
            this.sessionLength = Duration.between(start, end).isNegative() ? Duration.ZERO : Duration.between(start, end);

            return this;
        }

        public Builder times(Duration active, Duration idle) {
            this.activeTime = active;
            this.idleTime = idle;

            return this;
        }

        public Builder compilationSucceeded() {
            compilationsSucceeded++;
            return this;
        }

        public Builder compilationFailed() {
            compilationsFailed++;
            return this;
        }

        public Builder execution(ExecutionOutcome outcome) {
            switch (outcome) {
                case NORMAL -> executionsNormal++;
                case EXCEPTION -> executionsWithException++;
                case TERMINATED -> executionsTerminated++;
                default -> executionsUnknown++;
            }

            return this;
        }

        public Builder error(String category) {
            errorFrequency.merge(category, 1, Integer::sum);
            return this;
        }

        public Builder secondsBetweenCompilation(long seconds) {
            secondsBetweenCompilations.add(seconds);
            return this;
        }

        public Builder timelineEntry(ClassTimelineEntry entry) {
            classTimeline.add(entry);
            return this;
        }

        public Builder editorChange() {
            editorChanges++;
            return this;
        }

        public Builder edit(int edits, int typedChars, int bulkChars, int deletedChars) {
            textEdits += edits;
            charactersTyped += typedChars;
            charactersBulkInserted += bulkChars;
            charactersDeleted += deletedChars;

            return this;
        }

        public Builder typingSeconds(int seconds) {
            typingSeconds = seconds;
            return this;
        }

        public Builder runtimeException(String exceptionClass) {
            exceptionFrequency.merge(exceptionClass, 1, Integer::sum);
            return this;
        }

        public Builder classCreated() {
            classesCreated++;
            return this;
        }

        public Builder classDeleted() {
            classesDeleted++;
            return this;
        }

        public Builder classModified() {
            classesModified++;
            return this;
        }

        public Builder errorCode(String code) {
            errorCodeFrequency.merge(code, 1, Integer::sum);
            return this;
        }

        public Builder projectOpened() {
            projectsOpened++;
            return this;
        }

        public Builder projectCreated() {
            projectsCreated++;
            return this;
        }

        public Builder projectDeleted() {
            projectsDeleted++;
            return this;
        }

        public SessionSummary build() {
            sortByCountDescending(errorFrequency);
            sortByCountDescending(errorCodeFrequency);
            sortByCountDescending(exceptionFrequency);

            return new SessionSummary(this);
        }

        private static void sortByCountDescending(Map<String, Integer> counts) {
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
            sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
            counts.clear();

            for (Map.Entry<String, Integer> entry : sorted) {
                counts.put(entry.getKey(), entry.getValue());
            }
        }
    }
}