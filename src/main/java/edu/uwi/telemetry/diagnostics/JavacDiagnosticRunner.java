package edu.uwi.telemetry.diagnostics;

import edu.uwi.telemetry.io.FileSupport;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Uses the Java compiler to check source files for errors.
 * Collects details such as the file name, line number, and error code,
 * while limiting the number of errors recorded. It also uses a
 * temporary directory for compilation and then cleans it.
 *
 * @author Micah Mitchell
 */

public final class JavacDiagnosticRunner implements DiagnosticRunner {
    static final int MAX_DIAGNOSTICS = 50;

    @Override
    public List<CompilerDiagnostic> diagnose(List<Path> sourceFiles) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return List.of();
        }

        List<File> sources = sourceFiles.stream().filter(p -> p.getFileName().toString().endsWith(".java") && Files.isRegularFile(p)).map(Path::toFile).toList();

        if (sources.isEmpty()) {
            return List.of();
        }

        Path outputDir = null;

        try {
            outputDir = Files.createTempDirectory("bluej-telemetry-javac");
            DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
            try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(collector, Locale.ROOT, StandardCharsets.UTF_8)) {
                Path projectDir = sources.get(0).toPath().toAbsolutePath().getParent();
                compiler.getTask(null, fileManager, collector, options(outputDir, projectDir), null, fileManager.getJavaFileObjectsFromFiles(sources)).call();
            }

            return errorsOf(collector);
        }

        catch (IOException | RuntimeException ex) {
            return List.of();
        }

        finally {
            FileSupport.deleteTree(outputDir);
        }
    }

    static List<String> options(Path outputDir, Path projectDir) throws IOException {
        List<String> options = new ArrayList<>(List.of("-d", outputDir.toString(), "-proc:none", "-implicit:none", "-nowarn", "-encoding", "UTF-8", "-sourcepath", projectDir.toString()));
        options.add("-cp");
        options.add(classPath(outputDir, projectDir));

        return options;
    }

    static String classPath(Path outputDir, Path projectDir) throws IOException {
        Path libs = projectDir.resolve("+libs");
        List<String> entries = new ArrayList<>();
        if (Files.isDirectory(libs)) {
            try (Stream<Path> jars = Files.list(libs)) {
                jars.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().forEach(p -> entries.add(p.toString()));
            }
        }

        if (entries.isEmpty()) {
            entries.add(outputDir.toString());
        }

        return String.join(File.pathSeparator, entries);
    }

    private static List<CompilerDiagnostic> errorsOf(DiagnosticCollector<JavaFileObject> collector) {
        List<CompilerDiagnostic> result = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : collector.getDiagnostics()) {
            if (d.getKind() != Diagnostic.Kind.ERROR || result.size() >= MAX_DIAGNOSTICS) {
                continue;
            }

            String file = d.getSource() == null ? "unknown" : new File(d.getSource().getName()).getName();
            int line = d.getLineNumber() == Diagnostic.NOPOS ? 0 : (int) d.getLineNumber();
            result.add(new CompilerDiagnostic(file, line, d.getCode() == null ? "unknown" : d.getCode()));
        }

        return result;
    }
}