package de.thm.mni.wikidata.neo4j.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ProgressTrackerTest {

    @Test
    void measuresNodeImportByTheBytesOfTheFirstPass() {
        var tracker = new ProgressTracker(200);

        tracker.add(50);

        assertThat(tracker.snapshot().phase()).isEqualTo(ImportPhase.NODES);
        assertThat(tracker.snapshot().fraction()).isEqualTo(0.25);
    }

    @Test
    void measuresRelationshipImportByTheBytesOfTheSecondPass() {
        var tracker = new ProgressTracker(200);
        tracker.add(200);
        tracker.stageStarted(ImportPhase.RELATIONSHIPS, "");

        tracker.add(150);

        assertThat(tracker.snapshot().fraction()).isEqualTo(0.75);
    }

    @Test
    void neverExceedsOne() {
        var tracker = new ProgressTracker(100);

        tracker.add(150);

        assertThat(tracker.snapshot().fraction()).isEqualTo(1.0);
    }

    @Test
    void emptyInputIsComplete() {
        assertThat(new ProgressTracker(0).snapshot().fraction()).isEqualTo(1.0);
    }

    @Test
    void takesTheProgressOfLaterPhasesFromTheImporter() {
        var tracker = new ProgressTracker(100);
        tracker.add(200);
        tracker.stageStarted(ImportPhase.LINKING, "Node Degrees");

        tracker.phaseProgress(0.4);

        assertThat(tracker.snapshot())
                .extracting(ProgressSnapshot::phase, ProgressSnapshot::stage, ProgressSnapshot::fraction)
                .containsExactly(ImportPhase.LINKING, "Node Degrees", 0.4);
    }

    @Test
    void keepsTheProgressWhenAnotherStageOfTheSamePhaseStarts() {
        var tracker = new ProgressTracker(100);
        tracker.stageStarted(ImportPhase.LINKING, "Node Degrees");
        tracker.phaseProgress(0.4);

        tracker.stageStarted(ImportPhase.LINKING, "Relationship --> Relationship");

        assertThat(tracker.snapshot().fraction()).isEqualTo(0.4);
        assertThat(tracker.snapshot().stage()).isEqualTo("Relationship --> Relationship");
    }

    @Test
    void startsANewPhaseAtZero() {
        var tracker = new ProgressTracker(100);
        tracker.stageStarted(ImportPhase.LINKING, "Node Degrees");
        tracker.phaseProgress(1.0);

        tracker.stageStarted(ImportPhase.POST_PROCESSING, "Count groups");

        assertThat(tracker.snapshot().fraction()).isZero();
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

        assertThat(tracker.snapshot().fraction()).isEqualTo(1.0);
    }
}
