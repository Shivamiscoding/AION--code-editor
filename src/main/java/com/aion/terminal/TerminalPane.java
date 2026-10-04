package com.aion.terminal;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Shell console with a protected output history and a separate command prompt. */
public class TerminalPane extends StackPane {
    private final TextArea output = new TextArea();
    private final TextField input = new TextField();
    private Process process;
    private BufferedWriter writer;
    private Consumer<String> outputListener;

    public TerminalPane() {
        setStyle("-fx-background-color: #17191f;");
        output.setEditable(false);
        output.setWrapText(false);
        output.setStyle("-fx-control-inner-background: #17191f; -fx-text-fill: #b8c5d8; -fx-font-family: monospace; -fx-font-size: 13px;");
        input.setPromptText("Enter a shell command and press Enter");
        input.setStyle("-fx-control-inner-background: #20232b; -fx-text-fill: #e5e9f0; -fx-font-family: monospace;");
        BorderPane console = new BorderPane();
        console.setCenter(output);
        console.setBottom(input);
        VBox.setVgrow(output, Priority.ALWAYS);
        getChildren().add(console);
        input.setOnAction(event -> {
            String command = input.getText(); input.clear();
            executeCommand(command);
        });
        startShell();
    }

    private void startShell() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String shell = os.contains("win") ? "cmd.exe" : "/bin/bash";
            ProcessBuilder builder = new ProcessBuilder(shell);
            builder.redirectErrorStream(true);
            process = builder.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            Thread readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    char[] buffer = new char[2048]; int count;
                    while ((count = reader.read(buffer)) != -1) {
                        String chunk = new String(buffer, 0, count);
                        Platform.runLater(() -> {
                            output.appendText(chunk);
                            if (outputListener != null) outputListener.accept(chunk);
                        });
                    }
                } catch (IOException exception) {
                    Platform.runLater(() -> append("\nShell closed: " + exception.getMessage() + "\n"));
                }
            }, "aion-terminal-output");
            readerThread.setDaemon(true); readerThread.start();
            append("AION terminal ready (" + shell + ").\n");
        } catch (IOException exception) {
            append("Failed to start terminal: " + exception.getMessage() + "\n");
        }
    }

    public void executeCommand(String command) {
        if (command == null || command.isBlank()) return;
        append("$ " + command + "\n");
        try {
            if (writer == null || process == null || !process.isAlive()) {
                append("Shell is not running. Restart the application to open a new terminal.\n"); return;
            }
            writer.write(command); writer.newLine(); writer.flush();
        } catch (IOException exception) { append("Terminal input failed: " + exception.getMessage() + "\n"); }
    }

    public void setOutputListener(Consumer<String> listener) { outputListener = listener; }
    private void append(String text) { output.appendText(text); }
}
