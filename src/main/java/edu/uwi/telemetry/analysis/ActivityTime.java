package edu.uwi.telemetry.analysis;

import java.time.Duration;

/**
 * Represents the amount of time a user spends interacting with BlueJ
 * and the amount of time spent inactive during a session.
 * 
 * @author Sawdah Kadir
 */

public record ActivityTime(Duration active, Duration idle) {}