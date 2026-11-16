package edu.uwi.telemetry.diagnostics;

import java.nio.file.Path;
import java.util.List;

/**
 * Defines an interface for handling Java compilation failures.
 * Provides a method to receive the paths of files involved in a
 * failed compilation and indicate whether the compilation was
 * initiated by the user.
 *
 * @author Micah Mitchell
 */

public interface CompileFailureListener {
    void compileFailed(List<Path> files, boolean userGenerated);
}
