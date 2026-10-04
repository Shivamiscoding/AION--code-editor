package com.aion.app;

import com.aion.editor.CodeEditorPane;
import com.aion.execution.RunEngine;
import com.aion.filesystem.FileExplorer;
import com.aion.terminal.TerminalPane;
import com.aion.timeline.TimelineManager;
import com.aion.timeline.TimelinePane;
import com.aion.toolchain.ToolchainManager;
import javafx.application.Application;
import javafx.geometry.Orientation;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AionApp extends Application {
    private final TabPane editorTabs = new TabPane();
    private final Label status = new Label("Ready");
    private final TextArea diagnostics = new TextArea();
    private FileExplorer explorer;
    private TimelineManager timelineManager;
    private TimelinePane timelinePane;
    private TerminalPane terminal;
    private RunEngine runEngine;
    private Stage stage;
    private final ListView<SymbolEntry> outline = new ListView<>();
    private final java.util.Map<CodeEditorPane, PauseTransition> editTimers = new java.util.IdentityHashMap<>();
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^\\s*(?:import\\s+.+|from\\s+.+\\s+import\\s+.+|package\\s+.+|#include\\s+.+|(?:public\\s+|private\\s+|protected\\s+|static\\s+|export\\s+|async\\s+)*(?:class|interface|enum|struct|record|function|def|fn|func|const|let|var|type|namespace)\\s+[^;]+|(?:public\\s+|private\\s+|protected\\s+|static\\s+|final\\s+|volatile\\s+)*(?:[A-Za-z_$][\\w$<>\\[\\].?,]*\\s+)+[A-Za-z_$][\\w$]*\\s*(?:\\([^;]*\\)\\s*(?:\\{|;)|(?:=[^;]*)?;))");

    private static final class SymbolEntry {
        final int line; final String kind; final String text;
        SymbolEntry(int line, String text) {
            this.line = line; this.text = text.trim();
            this.kind = this.text.startsWith("import ") || this.text.startsWith("#include") ? "Import" :
                    this.text.startsWith("package ") ? "Package" :
                    this.text.matches(".*\\b(class|interface|enum|struct|record)\\b.*") ? "Type" :
                    this.text.matches(".*\\b(function|def|fn|func)\\b.*") ? "Function" : "Declaration";
        }
        @Override public String toString() { return kind + "  " + text; }
    }

    @Override public void start(Stage primaryStage) {
        stage = primaryStage;
        Path projectPath = Paths.get(".").toAbsolutePath().normalize();
        explorer = new FileExplorer(projectPath);
        timelineManager = new TimelineManager();
        timelinePane = new TimelinePane(timelineManager);
        timelinePane.setOnRestore(snapshot -> {
            CodeEditorPane editor = activeEditor();
            if (editor == null) { newFile(); editor = activeEditor(); }
            editor.replaceText(snapshot.content);
            diagnostics.setText("Restored " + snapshot.timestamp + " · " + snapshot.eventType + " from " + snapshot.file);
            status.setText("Restored timeline snapshot");
        });
        terminal = new TerminalPane();
        terminal.setOutputListener(chunk -> {
            diagnostics.appendText(chunk);
        });
        ToolchainManager toolchains = new ToolchainManager();
        runEngine = new RunEngine(toolchains, terminal);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(buildToolbar());
        root.setLeft(buildExplorer());
        root.setRight(buildRightRail(toolchains));
        root.setCenter(buildWorkArea());
        root.setBottom(status);
        configureOutline();

        Scene scene = new Scene(root, 1440, 900);
        scene.getStylesheets().add(getClass().getResource("/css/editor.css").toExternalForm());
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.S, javafx.scene.input.KeyCombination.SHORTCUT_DOWN), this::saveActive);
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.O, javafx.scene.input.KeyCombination.SHORTCUT_DOWN), this::openFile);
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.N, javafx.scene.input.KeyCombination.SHORTCUT_DOWN), this::newFile);
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F6), this::runActive);
        primaryStage.setTitle("AION — " + projectPath.getFileName());
        primaryStage.setScene(scene);
        primaryStage.show();
        newFile();
    }

    private ToolBar buildToolbar() {
        Button open = new Button("Open Folder"); open.setOnAction(e -> openFolder());
        Button file = new Button("Open File"); file.setOnAction(e -> openFile());
        Button create = new Button("New File"); create.setOnAction(e -> newFile());
        Button save = new Button("Save"); save.setOnAction(e -> saveActive());
        Button compile = new Button("Compile"); compile.setOnAction(e -> compileActive());
        Button run = new Button("▶ Run"); run.getStyleClass().add("run-button"); run.setOnAction(e -> runActive());
        Button settings = new Button("Settings / Runtimes"); settings.setOnAction(e -> showToolchains());
        Button help = new Button("Coding Help"); help.setOnAction(e -> showHelpWindow());
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label brand = new Label("AION  /  CODE WORKSPACE"); brand.getStyleClass().add("brand");
        return new ToolBar(brand, new Separator(), open, file, create, save, spacer, compile, run, settings, help);
    }

    private VBox buildExplorer() {
        Label heading = new Label("PROJECT"); heading.getStyleClass().add("section-heading");
        explorer.setPrefWidth(250);
        explorer.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && explorer.getSelectionModel().getSelectedItem() != null) {
                Path path = explorer.getSelectionModel().getSelectedItem().getValue();
                if (Files.isRegularFile(path)) openPath(path);
            }
        });
        Label outlineHeading = new Label("OUTLINE · imports, types, declarations"); outlineHeading.getStyleClass().add("section-heading");
        outline.setPlaceholder(new Label("Open a source file to see its outline"));
        outline.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(SymbolEntry item, boolean empty) {
                super.updateItem(item, empty); setText(empty || item == null ? null : item.toString());
                setTooltip(empty || item == null ? null : new Tooltip("Line " + item.line + " · right-click to remove this declaration"));
            }
        });
        outline.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                SymbolEntry selected = outline.getSelectionModel().getSelectedItem();
                CodeEditorPane editor = activeEditor(); if (selected != null && editor != null) editor.goToLine(selected.line);
            }
        });
        ContextMenu menu = new ContextMenu(); MenuItem delete = new MenuItem("Delete this line from source");
        delete.setOnAction(event -> { SymbolEntry selected = outline.getSelectionModel().getSelectedItem(); CodeEditorPane editor = activeEditor(); if (selected != null && editor != null) { editor.removeLine(selected.line); refreshOutline(editor); } });
        menu.getItems().add(delete); outline.setContextMenu(menu);
        VBox upper = new VBox(6, heading, explorer); VBox.setVgrow(explorer, Priority.ALWAYS);
        VBox lower = new VBox(6, outlineHeading, outline); VBox.setVgrow(outline, Priority.ALWAYS);
        SplitPane leftPanels = new SplitPane(upper, lower); leftPanels.setOrientation(Orientation.VERTICAL); leftPanels.setDividerPositions(0.68);
        VBox box = new VBox(leftPanels); VBox.setVgrow(leftPanels, Priority.ALWAYS); box.getStyleClass().add("side-panel");
        return box;
    }

    private VBox buildRightRail(ToolchainManager toolchains) {
        Label heading = new Label("TIMELINE"); heading.getStyleClass().add("section-heading");
        timelinePane.setPrefWidth(250); VBox.setVgrow(timelinePane, Priority.ALWAYS);
        Button tools = new Button("Available runtimes"); tools.setMaxWidth(Double.MAX_VALUE); tools.setOnAction(e -> showToolchains());
        Button help = new Button("Mini window · coding resources"); help.setMaxWidth(Double.MAX_VALUE); help.setOnAction(e -> showHelpWindow());
        VBox box = new VBox(8, heading, timelinePane, tools, help); box.getStyleClass().add("side-panel");
        return box;
    }

    private SplitPane buildWorkArea() {
        editorTabs.getStyleClass().add("editor-tabs");
        terminal.setPrefHeight(210);
        diagnostics.setEditable(false); diagnostics.setPromptText("Compiler and run diagnostics appear here."); diagnostics.getStyleClass().add("diagnostics");
        SplitPane lower = new SplitPane(terminal, diagnostics); lower.setOrientation(Orientation.HORIZONTAL); lower.setDividerPositions(0.78);
        SplitPane vertical = new SplitPane(editorTabs, lower); vertical.setOrientation(Orientation.VERTICAL); vertical.setDividerPositions(0.69);
        return vertical;
    }

    private void newFile() {
        CodeEditorPane editor = new CodeEditorPane();
        Tab tab = new Tab("Untitled", editor); tab.setUserData(editor);
        tab.setOnCloseRequest(e -> { if (editor.isDirty() && !confirmDiscard()) e.consume(); });
        editor.setOnDirtyChanged(() -> tab.setText((editor.isDirty() ? "● " : "") + (editor.getCurrentFile() == null ? "Untitled" : editor.getCurrentFile().getFileName().toString())));
        installEditorHooks(editor);
        editorTabs.getTabs().add(tab); editorTabs.getSelectionModel().select(tab);
        status.setText("New file · Ctrl/Cmd+S to save");
    }

    private void openFolder() {
        DirectoryChooser chooser = new DirectoryChooser(); chooser.setTitle("Open Project Folder");
        File selected = chooser.showDialog(stage);
        if (selected != null) { explorer.setRootPath(selected.toPath()); stage.setTitle("AION — " + selected.getName()); status.setText("Project: " + selected.getAbsolutePath()); }
    }

    private void openFile() {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Open source file");
        File selected = chooser.showOpenDialog(stage); if (selected != null) openPath(selected.toPath());
    }

    private void openPath(Path path) {
        for (Tab tab : editorTabs.getTabs()) {
            if (tab.getContent() instanceof CodeEditorPane && ((CodeEditorPane) tab.getContent()).getCurrentFile() != null && path.toAbsolutePath().normalize().equals(((CodeEditorPane) tab.getContent()).getCurrentFile().toAbsolutePath().normalize())) {
                editorTabs.getSelectionModel().select(tab); return;
            }
        }
        CodeEditorPane editor = new CodeEditorPane(); editor.loadFile(path);
        Tab tab = new Tab(path.getFileName().toString(), editor); tab.setUserData(editor);
        tab.setOnCloseRequest(e -> { if (editor.isDirty() && !confirmDiscard()) e.consume(); });
        editor.setOnDirtyChanged(() -> tab.setText((editor.isDirty() ? "● " : "") + path.getFileName()));
        installEditorHooks(editor);
        editorTabs.getTabs().add(tab); editorTabs.getSelectionModel().select(tab);
        timelineManager.addSnapshot(path.toString(), "Opened " + path.getFileName(), editor.getText()); timelinePane.refresh();
        status.setText(path.toAbsolutePath().toString());
    }

    private CodeEditorPane activeEditor() {
        Tab tab = editorTabs.getSelectionModel().getSelectedItem();
        return tab != null && tab.getContent() instanceof CodeEditorPane ? (CodeEditorPane) tab.getContent() : null;
    }
    private void saveActive() {
        CodeEditorPane editor = activeEditor(); if (editor == null) return;
        try {
            if (editor.getCurrentFile() == null) {
                FileChooser chooser = new FileChooser(); chooser.setTitle("Save source file");
                File file = chooser.showSaveDialog(stage); if (file == null) return;
                editor.saveFile(file.toPath());
            } else editor.saveFile();
        } catch (RuntimeException exception) { diagnostics.setText(exception.getMessage()); status.setText("Save failed"); return; }
        Tab tab = editorTabs.getSelectionModel().getSelectedItem(); tab.setText(editor.getCurrentFile().getFileName().toString());
        timelineManager.addSnapshot(editor.getCurrentFile().toString(), "Saved", editor.getText()); timelinePane.refresh();
        status.setText("Saved " + editor.getCurrentFile());
    }
    private void compileActive() {
        CodeEditorPane editor = activeEditor(); if (editor == null) return;
        saveActive(); if (editor.getCurrentFile() == null) return;
        diagnostics.setText("Compiling " + editor.getCurrentFile().getFileName() + "…\nSee Terminal for compiler output.");
        runEngine.compileFile(editor.getCurrentFile()); status.setText("Compile requested: " + editor.getCurrentFile().getFileName());
    }
    private void runActive() {
        CodeEditorPane editor = activeEditor(); if (editor == null) return;
        saveActive(); if (editor.getCurrentFile() == null) return;
        timelineManager.addSnapshot(editor.getCurrentFile().toString(), "Run", editor.getText()); timelinePane.refresh();
        diagnostics.setText("Running " + editor.getCurrentFile().getFileName() + "…\nSee Terminal for program output.");
        runEngine.runFile(editor.getCurrentFile()); status.setText("Running " + editor.getCurrentFile().getFileName());
    }
    private boolean confirmDiscard() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "This file has unsaved changes. Close it?", ButtonType.CANCEL, ButtonType.OK);
        alert.setHeaderText("Unsaved changes"); return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    private void showToolchains() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION); alert.setTitle("Language toolchains"); alert.setHeaderText("Detected on this computer");
        alert.setContentText(runEngine.getToolchainSummary()); alert.showAndWait();
    }

    private void configureOutline() {
        editorTabs.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab != null && newTab.getContent() instanceof CodeEditorPane) refreshOutline((CodeEditorPane) newTab.getContent());
            else outline.getItems().clear();
        });
    }

    private void installEditorHooks(CodeEditorPane editor) {
        editor.setOnContentChanged(() -> {
            refreshOutline(editor);
            PauseTransition timer = editTimers.computeIfAbsent(editor, key -> {
                PauseTransition pause = new PauseTransition(Duration.millis(650));
                pause.setOnFinished(event -> {
                    String file = editor.getCurrentFile() == null ? "Untitled" : editor.getCurrentFile().toString();
                    timelineManager.addSnapshot(file, "Edited", editor.getText()); timelinePane.refresh();
                });
                return pause;
            });
            timer.playFromStart();
        });
    }

    private void refreshOutline(CodeEditorPane editor) {
        if (activeEditor() != editor) return;
        List<SymbolEntry> entries = new ArrayList<>();
        String[] lines = editor.getText().split("\\n", -1);
        for (int i = 0; i < lines.length; i++) if (SYMBOL_PATTERN.matcher(lines[i]).matches()) entries.add(new SymbolEntry(i + 1, lines[i]));
        outline.getItems().setAll(entries);
    }

    private void showHelpWindow() {
        Stage help = new Stage(); help.initOwner(stage); help.setTitle("AION · Coding Resources");
        VBox content = new VBox(12); content.setPadding(new Insets(18)); content.setStyle("-fx-background-color: #20232b;");
        Label title = new Label("Quick coding resources"); title.getStyleClass().add("brand");
        content.getChildren().addAll(title, helpLink("Java documentation", "https://docs.oracle.com/en/java/"), helpLink("Python documentation", "https://docs.python.org/3/"), helpLink("MDN · JavaScript and web", "https://developer.mozilla.org/"), helpLink("C++ reference", "https://en.cppreference.com/"), helpLink("GitHub guides", "https://docs.github.com/"));
        Scene scene = new Scene(content, 340, 300); scene.getStylesheets().add(getClass().getResource("/css/editor.css").toExternalForm());
        help.setScene(scene); help.show();
    }

    private Hyperlink helpLink(String label, String url) {
        Hyperlink link = new Hyperlink(label);
        link.setOnAction(event -> getHostServices().showDocument(url));
        return link;
    }
}
