package com.aion.timeline;

import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

public class TimelinePane extends VBox {
    
    private final ListView<TimelineManager.Snapshot> listView;
    private final TimelineManager manager;

    public TimelinePane(TimelineManager manager) {
        this.manager = manager;
        this.setStyle("-fx-background-color: #252526; -fx-padding: 10px;");
        
        Label title = new Label("CODE TIMELINE");
        title.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold;");
        
        listView = new ListView<>();
        listView.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: #cccccc;");
        listView.setCellFactory(view -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(TimelineManager.Snapshot snapshot, boolean empty) {
                super.updateItem(snapshot, empty);
                setText(empty || snapshot == null ? null : snapshot.timestamp + "  ·  " + snapshot.eventType);
                setTooltip(empty || snapshot == null ? null : new Tooltip(snapshot.file));
            }
        });
        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && listView.getSelectionModel().getSelectedItem() != null) {
                TimelineManager.Snapshot snapshot = listView.getSelectionModel().getSelectedItem();
                if (onRestore != null) onRestore.accept(snapshot);
            }
        });
        
        this.getChildren().addAll(title, listView);
    }

    public void refresh() {
        listView.getItems().setAll(manager.getHistory());
    }

    private java.util.function.Consumer<TimelineManager.Snapshot> onRestore;
    public void setOnRestore(java.util.function.Consumer<TimelineManager.Snapshot> callback) { onRestore = callback; }
}
