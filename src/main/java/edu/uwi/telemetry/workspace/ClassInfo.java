package edu.uwi.telemetry.workspace;

/**
 * Immutable record describing one class in an open project: the project name, the class
 * name and its method count. The method count is -1 when it is unknown, for example because the class is
 * not compiled yet. Part of a {@link WorkspaceSnapshot} and compared by the {@link WorkspaceDiffer}.
 * @author Rinard Guy
 */

public record ClassInfo(String project, String className, int methodCount) {}