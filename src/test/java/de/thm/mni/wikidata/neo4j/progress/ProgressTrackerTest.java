package de.thm.mni.wikidata.neo4j.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ProgressTrackerTest {

    @Test
    void reportsFractionOfTotalBytes() {
        var tracker = new ProgressTracker(200);

        tracker.add(50);

        assertThat(tracker.fraction()).isEqualTo(0.25);
    }

    @Test
    void neverExceedsOne() {
        var tracker = new ProgressTracker(100);

        tracker.add(150);

        assertThat(tracker.fraction()).isEqualTo(1.0);
    }

    @Test
    void emptyInputIsComplete() {
        assertThat(new ProgressTracker(0).fraction()).isEqualTo(1.0);
    }

    @Test
    void sumsConcurrentUpdates() throws Exception {
        var tracker = new ProgressTracker(8_000);
        var executor = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 8; i++) {
            executor.submit(() -> {
                for (int j = 0; j < 1_000; j++) {
                    tracker.add(1);
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(tracker.fraction()).isEqualTo(1.0);
    }
}
