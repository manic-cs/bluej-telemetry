package edu.uwi.telemetry.diagnostics;

/**
 * Represents diagnostic information generated during Java compilation.
 * Stores the file name, line number, and diagnostic code associated
 * with th compiler issue.
 *
 * @author Micah Mitchell
 */

public record CompilerDiagnostic(String file, int line, String code) {}