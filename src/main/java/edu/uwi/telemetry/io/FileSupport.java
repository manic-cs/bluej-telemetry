package edu.uwi.telemetry.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Provides utility methods for writing, replacing and deleting files
 * and directories.
 * 
 * @author Sawdah Kadir
 */
public final class FileSupport {
    /**
     * Maximum number of attempts made when deleting a directory tree.
     */
    private static final int DELETE_ATTEMPTS = 10;
    /**
     * Delay in milliseconds between unsuccessful deletion attempts.
     */
    private static final long DELETE_RETRY_MILLIS = 100;

    private FileSupport() {}

    /**
     * Writes text to a temporary file and replaces the target file.
     */
    public static void writeAtomically(Path target, String text) throws IOException {
        Path temporary = temporaryFor(target);
        Files.writeString(temporary, text, StandardCharsets.UTF_8);
        replace(temporary, target);
    }

    /**
     * Creates the parent directory and determines the temporary file path.
     */

    public static Path temporaryFor(Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);

        return parent.resolve(target.getFileName() + ".tmp");
    }

    /**
     * Replaces the target file with the specified temporary file.
     */

    public static void replace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }

        catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Attempts to delete a directory and all its contents.
     */

    public static void deleteTree(Path root) {
        if (root == null) {
            return;
        }

        for (int attempt = 0; attempt < DELETE_ATTEMPTS && Files.exists(root); attempt++) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.sorted(Comparator.reverseOrder()).forEach(FileSupport::deleteQuietly);
            }

            catch (IOException | UncheckedIOException ex) {
            }

            if (Files.exists(root) && !pause()) {
                return;
            }
        }
    }

    /**
     * Attempts to delete a file or directory without propagating
     * deletion exceptions.
     */

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        }

        catch (IOException ex) {
        }
    }

    /**
     * Waits for the configured delay between deletion attempts.
     */

    private static boolean pause() {
        try {
            Thread.sleep(DELETE_RETRY_MILLIS);
            return true;
        }

        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}