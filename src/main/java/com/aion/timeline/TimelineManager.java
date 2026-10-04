package com.aion.timeline;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TimelineManager {
    
    public static class Snapshot {
        public final String id;
        public final String timestamp;
        public final String file;
        public final String eventType;
        public final String content;

        public Snapshot(String file, String eventType, String content) {
            this.id = UUID.randomUUID().toString();
            this.file = file;
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            this.eventType = eventType;
            this.content = content;
        }
        
        @Override
        public String toString() {
            return timestamp + " " + eventType;
        }
    }

    private final List<Snapshot> history = new ArrayList<>();

    public void addSnapshot(String eventType, String content) { addSnapshot("", eventType, content); }

    public void addSnapshot(String file, String eventType, String content) {
        // Suppress identical consecutive snapshots for the same file.
        if (!history.isEmpty()) {
            Snapshot last = history.get(history.size() - 1);
            if (last.file.equals(file) && last.content.equals(content)) {
                return;
            }
        }
        history.add(new Snapshot(file, eventType, content));
    }

    public List<Snapshot> getHistory() {
        return java.util.Collections.unmodifiableList(history);
    }
}
