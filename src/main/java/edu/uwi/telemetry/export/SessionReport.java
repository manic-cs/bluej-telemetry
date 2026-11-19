package edu.uwi.telemetry.export;

import edu.uwi.telemetry.analysis.SessionSummary;
import edu.uwi.telemetry.model.SessionData;

/**
 * Combines the original telemetry session data with its calculated
 * statistical summary.
 */
public record SessionReport(SessionData data, SessionSummary summary) {}