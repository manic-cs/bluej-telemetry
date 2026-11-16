package edu.uwi.telemetry.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the detection of Java compiler errors, including error codes
 * and line numbers. Also checks valid source files, classpath handling,
 * missing files, and cleanup of temporary compilation files.
 *
 * @author Micah Mitchell
 */

class JavacDiagnosticRunnerTest {
    @TempDir
    Path project;

    private final JavacDiagnosticRunner runner = new JavacDiagnosticRunner();

    private static boolean noCompiler() {
        return ToolProvider.getSystemJavaCompiler() == null;
    }

    private Path write(String name, String source) throws IOException {
        return Files.writeString(project.resolve(name), source);
    }

    @Test
    void reportsRealJavacErrorCodesWithLineNumbers() throws IOException {
        if (noCompiler()) {
            return;
        }

        Path broken = write("Broken.java", "public class Broken {\n" + "    int a = \"text\";\n" + "    void m() { undefinedCall(); }\n" + "}\n");

        List<CompilerDiagnostic> found = runner.diagnose(List.of(broken));

        assertEquals(2, found.size());
        assertEquals(new CompilerDiagnostic("Broken.java", 2, "compiler.err.prob.found.req"), found.get(0));
        assertEquals("Broken.java", found.get(1).file());
        assertEquals(3, found.get(1).line());
        assertTrue(found.get(1).code().startsWith("compiler.err.cant.resolve"), found.get(1).code());
    }

    @Test
    void syntaxErrorsHaveTheirOwnCode() throws IOException {
        if (noCompiler()) {
            return;
        }

        Path file = write("Syntax.java", "public class Syntax {\n    int x = 1\n}\n");
        List<CompilerDiagnostic> found = runner.diagnose(List.of(file));
        assertEquals(1, found.size());
        assertEquals("compiler.err.expected", found.get(0).code());
    }

    @Test
    void validCodeProducesNoDiagnostics() throws IOException {
        if (noCompiler()) {
            return;
        }

        Path ok = write("Fine.java", "public class Fine { int f() { return 1; } }\n");
        assertTrue(runner.diagnose(List.of(ok)).isEmpty());
    }

    @Test
    void siblingClassesAreFoundThroughTheSourcePath() throws IOException {
        if (noCompiler()) {
            return;
        }
        write("Helper.java", "public class Helper { static int one() { return 1; } }\n");
        Path user = write("User.java", "public class User { int x = Helper.one(); }\n");
        assertTrue(runner.diagnose(List.of(user)).isEmpty(), "Helper must resolve, no false errors");
    }

    @Test
    void neverLeavesClassFilesInTheStudentsProject() throws IOException {
        if (noCompiler()) {
            return;
        }

        Path file = write("Clean.java", "public class Clean {}\n");
        runner.diagnose(List.of(file));

        try (Stream<Path> files = Files.list(project)) {
            assertEquals(List.of("Clean.java"), files.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void ignoresMissingAndNonJavaFiles() throws IOException {
        Path text = write("notes.txt", "hello");
        assertTrue(runner.diagnose(List.of(project.resolve("Missing.java"), text)).isEmpty());
        assertTrue(runner.diagnose(List.of()).isEmpty());
    }

    @Test
    void classPathUsesPlusLibsJarsWhenPresent() throws IOException {
        Path libs = Files.createDirectory(project.resolve("+libs"));
        Files.writeString(libs.resolve("b.jar"), "x");
        Files.writeString(libs.resolve("a.jar"), "x");
        Files.writeString(libs.resolve("readme.txt"), "x");

        String cp = JavacDiagnosticRunner.classPath(project.resolve("out"), project);

        assertEquals(libs.resolve("a.jar") + java.io.File.pathSeparator + libs.resolve("b.jar"), cp);
        assertFalse(cp.contains("readme"));
        assertEquals(project.resolve("out").toString(), JavacDiagnosticRunner.classPath(project.resolve("out"), project.resolve("nowhere")));
    }
}