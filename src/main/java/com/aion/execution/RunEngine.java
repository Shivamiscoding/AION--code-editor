package com.aion.execution;

import com.aion.terminal.TerminalPane;
import com.aion.toolchain.ToolchainManager;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.function.Consumer;

/** Dispatches source files to locally installed language runtimes. */
public class RunEngine {
    private final ToolchainManager toolchains;
    private final Consumer<String> commandSink;
    public RunEngine(ToolchainManager toolchains, TerminalPane terminal) { this(toolchains, terminal::executeCommand); }
    public RunEngine(ToolchainManager toolchains, Consumer<String> commandSink) { this.toolchains = toolchains; this.commandSink = commandSink; }

    public void compileFile(Path file) {
        if (file == null) { execute("echo 'No file selected.'"); return; }
        String ext = extension(file);
        String name = quote(file.getFileName().toString());
        String dir = quote(file.toAbsolutePath().getParent().toString());
        switch (ext) {
            case "java": if (available("javac")) execute("cd " + dir + " && javac -d . *.java"); else missing("javac", ext); break;
            case "c": if (available("clang")) execute("cd " + dir + " && clang *.c -o " + quote(stem(file))); else if (available("gcc")) execute("cd " + dir + " && gcc *.c -o " + quote(stem(file))); else missing("clang or gcc", ext); break;
            case "cpp": case "cc": case "cxx": if (available("clang++")) execute("cd " + dir + " && clang++ *." + ext + " -o " + quote(stem(file))); else if (available("g++")) execute("cd " + dir + " && g++ *." + ext + " -o " + quote(stem(file))); else missing("clang++ or g++", ext); break;
            default: execute("echo 'No separate compile step for ." + ext + " files.'");
        }
    }

    public void runFile(Path file) {
        if (file == null) { execute("echo 'No file selected.'"); return; }
        String ext = extension(file), path = quote(file.toAbsolutePath().toString());
        String dir = quote(file.toAbsolutePath().getParent().toString()), name = quote(file.getFileName().toString());
        switch (ext) {
            case "py": case "pyw": runIf("python3", "python3 " + path, "python " + path); break;
            case "java": if (available("javac") && available("java")) {
                String className = javaClassName(file);
                String sourceRoot = findJavaSourceRoot(file);
                if (sourceRoot != null) {
                    String relative = java.nio.file.Paths.get(sourceRoot).relativize(file.toAbsolutePath()).toString();
                    execute("cd " + quote(sourceRoot) + " && javac -d . " + quote(relative) + " && java " + quote(className));
                } else {
                    execute("cd " + dir + " && javac -d . " + name + " && java " + quote(className));
                }
            } else missing("javac and java", ext); break;
            case "c": compileThenRun(file, "*.c", available("clang") ? "clang" : "gcc", "clang", "gcc"); break;
            case "cpp": case "cc": case "cxx": compileThenRun(file, "*." + ext, available("clang++") ? "clang++" : "g++", "clang++", "g++"); break;
            case "js": case "mjs": case "cjs": runIf("node", "node " + path); break;
            case "ts": runIf("tsx", "tsx " + path); break;
            case "rb": runIf("ruby", "ruby " + path); break;
            case "php": runIf("php", "php " + path); break;
            case "go": runIf("go", "cd " + dir + " && go run ."); break;
            case "rs": if (available("rustc")) execute("cd " + dir + " && rustc " + name + " -o " + quote(stem(file)) + " && ./" + quote(stem(file))); else missing("rustc", ext); break;
            case "swift": runIf("swift", "swift " + path); break;
            case "sh": runIf("bash", "bash " + path); break;
            case "html": case "htm": openInBrowser(file); break;
            default: execute("echo 'Unsupported runnable file: ." + ext + ". Detected runtimes: " + getAvailableToolchains().replace("'", "") + "'");
        }
    }

    private void compileThenRun(Path file, String extPattern, String compiler, String... choices) {
        boolean found = false; for (String choice : choices) if (available(choice)) { compiler = choice; found = true; break; }
        if (!found) { missing(String.join(" or ", choices), extension(file)); return; }
        execute("cd " + quote(file.toAbsolutePath().getParent().toString()) + " && " + compiler + " " + extPattern + " -o " + quote(stem(file)) + " && ./" + quote(stem(file)));
    }
    private void runIf(String command, String... alternatives) {
        for (int i = 0; i < alternatives.length; i++) {
            String tool = i == 0 ? command : alternatives[i].substring(0, alternatives[i].indexOf(' '));
            if (available(tool)) { execute(alternatives[i]); return; }
        }
        missing(command, "source");
    }
    private String javaClassName(Path file) {
        try {
            String source = Files.readString(file);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(source);
            return (matcher.find() ? matcher.group(1) + "." : "") + stem(file);
        } catch (IOException ignored) { return stem(file); }
    }
    private void openInBrowser(Path file) {
        try {
            if (!java.awt.Desktop.isDesktopSupported()) { execute("echo 'Opening a browser is not supported on this desktop.'"); return; }
            java.awt.Desktop.getDesktop().browse(file.toAbsolutePath().toUri());
            execute("echo 'Opened " + file.getFileName().toString().replace("'", "") + " in the default browser.'");
        } catch (Exception exception) { execute("echo 'Could not open this page in a browser.'"); }
    }
    private boolean available(String command) { return toolchains.isAvailable(command); }
    private void missing(String command, String extension) { execute("echo 'Cannot run ." + extension + ": required toolchain " + command + " was not detected.'"); }
    private void execute(String command) { commandSink.accept(command); }
    private static String extension(Path file) { String n = file.getFileName().toString(); int i = n.lastIndexOf('.'); return i < 0 ? "" : n.substring(i + 1).toLowerCase(); }
    private static String stem(Path file) { String n = file.getFileName().toString(); int i = n.lastIndexOf('.'); return i < 0 ? n : n.substring(0, i); }
    private static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
    public String getAvailableToolchains() {
        return toolchains.getToolchains().entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).sorted().collect(Collectors.joining(", "));
    }
    public String getToolchainSummary() {
        return toolchains.getToolchains().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> (e.getValue() ? "✓  " : "—  ") + e.getKey()).collect(Collectors.joining("\n"));
    }
}
