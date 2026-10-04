package com.aion.filesystem;

import javafx.scene.control.TreeCell;
import javafx.scene.control.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;
import java.util.Optional;

public class FileExplorer extends TreeView<Path> {

    public FileExplorer(Path rootPath) {
        super();
        this.setStyle("-fx-control-inner-background: #1e1e1e; -fx-background-color: #1e1e1e;");
        
        this.setCellFactory(tv -> new TreeCell<Path>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                } else {
                    setText(item.getFileName() != null ? item.getFileName().toString() : item.toString());
                    setStyle("-fx-text-fill: #cccccc; -fx-font-family: monospace;");
                    
                    ContextMenu contextMenu = new ContextMenu();
                    MenuItem newFile = new MenuItem("New File");
                    newFile.setOnAction(e -> {
                        TextInputDialog dialog = new TextInputDialog("newfile.txt");
                        dialog.setTitle("New File");
                        dialog.setHeaderText("Create a new file");
                        dialog.showAndWait().ifPresent(name -> {
                            try {
                                Path targetDir = Files.isDirectory(item) ? item : item.getParent();
                                Files.createFile(targetDir.resolve(name));
                                FileExplorer.this.refreshTree();
                            } catch (IOException ex) { ex.printStackTrace(); }
                        });
                    });
                    
                    MenuItem newFolder = new MenuItem("New Folder");
                    newFolder.setOnAction(e -> {
                        TextInputDialog dialog = new TextInputDialog("newfolder");
                        dialog.setTitle("New Folder");
                        dialog.setHeaderText("Create a new folder");
                        dialog.showAndWait().ifPresent(name -> {
                            try {
                                Path targetDir = Files.isDirectory(item) ? item : item.getParent();
                                Files.createDirectory(targetDir.resolve(name));
                                FileExplorer.this.refreshTree();
                            } catch (IOException ex) { ex.printStackTrace(); }
                        });
                    });
                    
                    MenuItem rename = new MenuItem("Rename");
                    rename.setOnAction(e -> {
                        TextInputDialog dialog = new TextInputDialog(item.getFileName().toString());
                        dialog.setTitle("Rename");
                        dialog.setHeaderText("Rename file/folder");
                        dialog.showAndWait().ifPresent(name -> {
                            try {
                                Files.move(item, item.resolveSibling(name));
                                FileExplorer.this.refreshTree();
                            } catch (IOException ex) { ex.printStackTrace(); }
                        });
                    });
                    
                    MenuItem delete = new MenuItem("Delete");
                    delete.setOnAction(e -> {
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Delete");
                        alert.setHeaderText("Delete " + item.getFileName() + "?");
                        alert.showAndWait().ifPresent(res -> {
                            if (res == ButtonType.OK) {
                                try {
                                    Files.delete(item);
                                    FileExplorer.this.refreshTree();
                                } catch (IOException ex) { ex.printStackTrace(); }
                            }
                        });
                    });
                    
                    contextMenu.getItems().addAll(newFile, newFolder, rename, delete);
                    setContextMenu(contextMenu);
                }
            }
        });

        setRootPath(rootPath);
        this.setShowRoot(true);
    }

    public void setRootPath(Path rootPath) {
        TreeItem<Path> rootItem = createNode(rootPath);
        rootItem.setExpanded(true);
        this.setRoot(rootItem);
    }

    private TreeItem<Path> createNode(Path path) {
        return new TreeItem<Path>(path) {
            private boolean isLeaf;
            private boolean isFirstTimeChildren = true;
            private boolean isFirstTimeLeaf = true;

            @Override
            public boolean isLeaf() {
                if (isFirstTimeLeaf) {
                    isFirstTimeLeaf = false;
                    isLeaf = !Files.isDirectory(getValue());
                }
                return isLeaf;
            }

            @Override
            public javafx.collections.ObservableList<TreeItem<Path>> getChildren() {
                if (isFirstTimeChildren) {
                    isFirstTimeChildren = false;
                    super.getChildren().setAll(buildChildren(this));
                }
                return super.getChildren();
            }
        };
    }

    private javafx.collections.ObservableList<TreeItem<Path>> buildChildren(TreeItem<Path> TreeItem) {
        Path path = TreeItem.getValue();
        if (path != null && Files.isDirectory(path)) {
            javafx.collections.ObservableList<TreeItem<Path>> children = javafx.collections.FXCollections.observableArrayList();
            try (Stream<Path> paths = Files.list(path)) {
                paths.sorted((p1, p2) -> {
                    boolean d1 = Files.isDirectory(p1);
                    boolean d2 = Files.isDirectory(p2);
                    if (d1 && !d2) return -1;
                    if (!d1 && d2) return 1;
                    return p1.getFileName().compareTo(p2.getFileName());
                }).forEach(p -> children.add(createNode(p)));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return children;
        }
        return javafx.collections.FXCollections.emptyObservableList();
    }

    public void refreshTree() {
        if (getRoot() != null) {
            Path currentRoot = getRoot().getValue();
            setRootPath(currentRoot);
        }
    }
}
