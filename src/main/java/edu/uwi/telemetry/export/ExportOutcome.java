package edu.uwi.telemetry.export;

import java.nio.file.Path;
import java.util.List;

/**
 * 
 * EStores the files generated and problems encountered during report export.
 * 
 * @author Sawdah Kadir
 */
public record ExportOutcome(List<Path> files, List<String> problems) {
    /**
     * Creates an export outcome with immutable copies of the supplied lists.
     */
    public ExportOutcome {
        files = List.copyOf(files);
        problems = List.copyOf(problems);
    }

    /**
     * Checks whether at least one file was exported without errors.
     */
    public boolean success() {
        return problems.isEmpty() && !files.isEmpty();
    }

    /**
     * Builds a text description of exported files and encountered problems.
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (!files.isEmpty()) {
            sb.append("Reports written:\n");
            files.forEach(f -> sb.append("  ").append(f).append('\n'));
        }

        problems.forEach(p -> sb.append(p).append('\n'));

        return sb.toString().strip();
    }
}