package edu.uwi.telemetry.analysis;

/**
 * Represents a recorded change to a class during a session.
 * 
 * @author Sawdah Kadir
 */

public record ClassTimelineEntry(long offsetSeconds, String className, String change, int methodCount) {}