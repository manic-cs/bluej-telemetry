package edu.uwi.telemetry.io;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests file-writing and directory-deletion operations provided by FileSupport.
 *
 * @author Sawdah Kadir
 */
class FileSupportTest {
    private Path root;

    @BeforeEach
    void createRoot() throws IOException {
        root = Files.createTempDirectory("file-support-test");
    }

    @AfterEach
    void cleanUp() {
        FileSupport.deleteTree(root);
    }

    @Test
    void writeAtomicallyCreatesFoldersAndLeavesNoTemporaryFile() throws IOException {
        Path target = root.resolve("a/b/report.json");

        FileSupport.writeAtomically(target, "{\"ok\":true}");

        assertEquals("{\"ok\":true}", Files.readString(target));
        assertFalse(Files.exists(root.resolve("a/b/report.json.tmp")));
    }

    @Test
    void writeAtomicallyReplacesAnExistingFile() throws IOException {
        Path target = root.resolve("report.json");
        FileSupport.writeAtomically(target, "old");
        FileSupport.writeAtomically(target, "new");
        assertEquals("new", Files.readString(target));
    }

    @Test
    void deleteTreeRemovesFoldersWithContent() throws IOException {
        Path nested = Files.createDirectories(root.resolve("x/y"));
        Files.writeString(nested.resolve("f.txt"), "data");

        FileSupport.deleteTree(root.resolve("x"));

        assertFalse(Files.exists(root.resolve("x")));
    }

    @Test
    void deleteTreeToleratesNullAndMissingPaths() {
        FileSupport.deleteTree(null);
        FileSupport.deleteTree(root.resolve("never-existed"));
        assertTrue(Files.exists(root));
    }
}