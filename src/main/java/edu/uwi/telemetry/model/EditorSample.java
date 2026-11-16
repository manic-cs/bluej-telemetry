package edu.uwi.telemetry.model;

/**
 * Represents a sample of editor activity within a BlueJ project.
 * Stores the project name, class name, cursor position, and total
 * number of lines in the editor at the time of sampling.
 *
 * @author Micah Mitchell
 */

public record EditorSample(String project, String className, int line, int column, int lineCount) {}