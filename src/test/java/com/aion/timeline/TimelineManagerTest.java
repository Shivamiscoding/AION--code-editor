package com.aion.timeline;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimelineManagerTest {
    @Test void storesDistinctFileVersionsAndSuppressesRepeatedContent() {
        TimelineManager timeline = new TimelineManager();
        timeline.addSnapshot("A.java", "Opened", "one");
        timeline.addSnapshot("A.java", "Edited", "one");
        timeline.addSnapshot("A.java", "Edited", "two");
        timeline.addSnapshot("B.java", "Edited", "two");

        assertEquals(3, timeline.getHistory().size());
        assertEquals("two", timeline.getHistory().get(1).content);
        assertEquals("B.java", timeline.getHistory().get(2).file);
        assertThrows(UnsupportedOperationException.class, () -> timeline.getHistory().clear());
    }
}
