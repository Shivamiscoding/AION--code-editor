package com.aion.execution;

import com.aion.toolchain.ToolchainManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class RunEngineTest {
    @TempDir Path temp;

    @Test void runsPythonSourceAndPreservesPathsWithSpaces() throws Exception {
        Path source = temp.resolve("hello world.py");
        Files.writeString(source, "print('PYTHON_OK')\n");
        String[] command = new String[1];
        RunEngine engine = new RunEngine(new ToolchainManager(Map.of("python3", true)), value -> command[0] = value);
        engine.runFile(source);
        assertTrue(command[0].contains("'" + source.toAbsolutePath() + "'"));
        assertEquals("PYTHON_OK\n", execute(command[0]));
    }

    @Test void compilesAndRunsJavaWithPackageDeclaration() throws Exception {
        Path source = temp.resolve("Main.java");
        Files.writeString(source, "package sample; public class Main { public static void main(String[] args) { System.out.println(\"JAVA_OK\"); } }\n");
        String[] command = new String[1];
        RunEngine engine = new RunEngine(new ToolchainManager(Map.of("javac", true, "java", true)), value -> command[0] = value);
        engine.runFile(source);
        assertTrue(command[0].contains("javac -d . 'Main.java' && java 'sample.Main'"));
        assertEquals("JAVA_OK\n", execute(command[0]));
    }

    @Test void reportsMissingToolchainsInsteadOfLaunchingACommand() {
        String[] command = new String[1];
        RunEngine engine = new RunEngine(new ToolchainManager(Map.of()), value -> command[0] = value);
        engine.runFile(temp.resolve("missing.py"));
        assertTrue(command[0].contains("required toolchain python3"));
    }

    private String execute(String command) throws Exception {
        Process process = new ProcessBuilder("/bin/bash", "-c", command).redirectErrorStream(true).start();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "language process timed out");
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.exitValue(), output);
        return output;
    }
}
