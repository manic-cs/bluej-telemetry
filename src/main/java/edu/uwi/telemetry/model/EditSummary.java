package edu.uwi.telemetry.model;

/**
 * Represents a summary of editing activity during a telemetry session.
 * Stores the number of edits, characters typed, characters involved in
 * bulk changes, and characters deleted.
 *
 * @author Micah Mitchell
 */

public record EditSummary(int edits, int typedChars, int bulkChars, int deletedChars) {}