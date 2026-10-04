package com.aion.toolchain;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ToolchainManager {
    
    private final Map<String, Boolean> toolchains = new HashMap<>();

    public ToolchainManager() {
        detectToolchain("python3");
        detectToolchain("python");
        detectToolchain("java");
        detectToolchain("javac");
        detectToolchain("clang");
        detectToolchain("gcc");
        detectToolchain("clang++");
        detectToolchain("g++");
        detectToolchain("node");
        detectToolchain("tsx");
        detectToolchain("bash");
        detectToolchain("ruby");
        detectToolchain("php");
        detectToolchain("go");
        detectToolchain("rustc");
        detectToolchain("swift");
    }

    public ToolchainManager(Map<String, Boolean> detectedToolchains) {
        toolchains.putAll(detectedToolchains);
    }

    private void detectToolchain(String command) {
        try {
            Process process = new ProcessBuilder(command, "--version").redirectErrorStream(true).start();
            boolean finished = process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly();
            toolchains.put(command, finished && process.exitValue() == 0);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            toolchains.put(command, false);
        }
    }

    public boolean isAvailable(String command) {
        return toolchains.getOrDefault(command, false);
    }

    public java.util.Map<String, Boolean> getToolchains() { return java.util.Collections.unmodifiableMap(toolchains); }
}
