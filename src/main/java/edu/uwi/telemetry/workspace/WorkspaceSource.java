package edu.uwi.telemetry.workspace;

/**
 * Port to BlueJ: lets the sampler read the workspace without knowing about the BlueJ API
 * (dependency inversion). {@code capture()} returns the current snapshot, or {@code null} when it could not
 * be taken this time, in which case the sampler skips that tick. The real implementation is
 * {@code BlueJWorkspaceSource}. Unit tests use lambdas.
 * @author Rinard Guy
 */

public interface WorkspaceSource {
    WorkspaceSnapshot capture();
}