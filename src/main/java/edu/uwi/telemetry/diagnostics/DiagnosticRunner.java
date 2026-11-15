package edu.uwi.telemetry.diagnostics;

import java.nio.file.Path;
import java.util.List;

/**
 * Defines an interface for checking Java source files for
 * compilation errors. Returns a list of diagnostics containing
 * details about any issues found.
 *
 * @author Micah Mitchell
 */

public interface DiagnosticRunner {
    List<CompilerDiagnostic> diagnose(List<Path> sourceFiles);
}