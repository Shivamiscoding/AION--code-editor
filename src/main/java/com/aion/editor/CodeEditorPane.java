package com.aion.editor;

import javafx.scene.layout.StackPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Duration;

public class CodeEditorPane extends StackPane {

    private final CodeArea codeArea;
    private Path currentFile;
    private boolean isDirty = false;
    private boolean suppressDirty = false;
    private Runnable onContentChanged;

    private static final String[] KEYWORDS = new String[] {
            "abstract", "assert", "boolean", "break", "byte",
            "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else",
            "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while",
            "def", "print", "String", "let", "var", "function", "const", "async", "await",
            "export", "from", "as", "True", "False", "None", "lambda", "pass", "yield",
            "fn", "func", "mut", "use", "mod", "pub", "impl", "trait", "match", "crate",
            "bool", "constexpr", "auto", "template", "typename", "using", "namespace",
            "package", "console", "null", "undefined", "true", "false", "try", "except",
            "with", "raise", "del", "global", "nonlocal", "range", "self", "super"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String PAREN_PATTERN = "\\(|\\)";
    private static final String BRACE_PATTERN = "\\{|\\}";
    private static final String BRACKET_PATTERN = "\\[|\\]";
    private static final String SEMICOLON_PATTERN = "\\;";
    private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'|`([^`\\\\]|\\\\.)*`";
    private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "#[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";

    private static final Pattern PATTERN = Pattern.compile(
            "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
            + "|(?<PAREN>" + PAREN_PATTERN + ")"
            + "|(?<BRACE>" + BRACE_PATTERN + ")"
            + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
            + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
            + "|(?<STRING>" + STRING_PATTERN + ")"
            + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
    );

    public CodeEditorPane() {
        this.setStyle("-fx-background-color: #1e1e1e;");
        codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #cccccc; -fx-font-family: monospace; -fx-font-size: 14px;");
        codeArea.textProperty().addListener((observable, oldText, newText) -> {
            if (!suppressDirty && !isDirty) {
                isDirty = true;
                notifyDirtyChanged();
            } else if (!suppressDirty) notifyDirtyChanged();
            if (onContentChanged != null) onContentChanged.run();
        });
        
        // Auto-indentation
        codeArea.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                int caretPosition = codeArea.getCaretPosition();
                int currentParagraph = codeArea.getCurrentParagraph();
                if (currentParagraph > 0) {
                    String prevLine = codeArea.getParagraph(currentParagraph - 1).getText();
                    Matcher m = Pattern.compile("^\\s+").matcher(prevLine);
                    if (m.find()) {
                        javafx.application.Platform.runLater(() -> codeArea.insertText(caretPosition, m.group()));
                    }
                }
            }
        });

        // Syntax Highlighting Debounce
        codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(300))
                .subscribe(ignore -> {
                    codeArea.setStyleSpans(0, computeHighlighting(codeArea.getText()));
                });

        this.getChildren().add(codeArea);
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while(matcher.find()) {
            String styleClass =
                    matcher.group("KEYWORD") != null ? "keyword" :
                    matcher.group("PAREN") != null ? "paren" :
                    matcher.group("BRACE") != null ? "brace" :
                    matcher.group("BRACKET") != null ? "bracket" :
                    matcher.group("SEMICOLON") != null ? "semicolon" :
                    matcher.group("STRING") != null ? "string" :
                    matcher.group("COMMENT") != null ? "comment" :
                    null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private Runnable onDirtyChanged;

    public void setOnDirtyChanged(Runnable listener) {
        this.onDirtyChanged = listener;
    }

    public void setOnContentChanged(Runnable listener) { this.onContentChanged = listener; }

    private void notifyDirtyChanged() {
        if (onDirtyChanged != null) javafx.application.Platform.runLater(onDirtyChanged);
    }

    public void loadFile(Path file) {
        try {
            String content = Files.readString(file);
            suppressDirty = true;
            codeArea.replaceText(content);
            suppressDirty = false;
            this.currentFile = file;
            isDirty = false;
            notifyDirtyChanged();
        } catch (IOException e) {
            suppressDirty = false;
            e.printStackTrace();
        }
    }

    public void saveFile() {
        if (currentFile != null) saveFile(currentFile);
    }

    public void saveFile(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(file, codeArea.getText());
            currentFile = file;
            isDirty = false;
            notifyDirtyChanged();
        } catch (IOException e) { throw new RuntimeException("Could not save " + file + ": " + e.getMessage(), e); }
    }
    
    public String getText() {
        return codeArea.getText();
    }

    public void replaceText(String text) {
        suppressDirty = true;
        codeArea.replaceText(text);
        suppressDirty = false;
        isDirty = true;
        notifyDirtyChanged();
    }

    public void goToLine(int line) {
        int target = Math.max(0, Math.min(line - 1, codeArea.getParagraphs().size() - 1));
        codeArea.moveTo(codeArea.getAbsolutePosition(target, 0));
        codeArea.requestFocus();
    }

    public void removeLine(int line) {
        String[] lines = getText().split("\\n", -1);
        if (line < 1 || line > lines.length) return;
        java.util.List<String> remaining = new java.util.ArrayList<>(java.util.Arrays.asList(lines));
        remaining.remove(line - 1);
        replaceText(String.join("\n", remaining));
    }
    
    public Path getCurrentFile() {
        return currentFile;
    }
    
    public boolean isDirty() {
        return isDirty;
    }
}
