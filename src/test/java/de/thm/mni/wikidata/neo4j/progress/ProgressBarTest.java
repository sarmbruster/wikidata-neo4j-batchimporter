package de.thm.mni.wikidata.neo4j.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProgressBarTest {

    @ParameterizedTest
    @CsvSource({
        "0, 0s",
        "59, 59s",
        "60, 1m 00s",
        "605, 10m 05s",
        "3600, 1h 00m",
        "7384, 2h 03m",
        "90000, 25h 00m"
    })
    void formatsDurations(long seconds, String expected) {
        assertThat(ProgressBar.format(Duration.ofSeconds(seconds))).isEqualTo(expected);
    }

    @Test
    void estimatesRemainingTimeFromTheRateSoFar() {
        assertThat(ProgressBar.eta(0.5, Duration.ofMinutes(10))).isEqualTo("10m 00s");
        assertThat(ProgressBar.eta(0.25, Duration.ofHours(1))).isEqualTo("3h 00m");
    }

    @Test
    void givesNoEstimateBeforeThereIsEnoughDataOrWhenThePhaseIsComplete() {
        assertThat(ProgressBar.eta(0.0, Duration.ofMinutes(5))).isEqualTo("--");
        assertThat(ProgressBar.eta(0.3, Duration.ofSeconds(1))).isEqualTo("--");
        assertThat(ProgressBar.eta(1.0, Duration.ofMinutes(5))).isEqualTo("--");
    }

    @Test
    void namesThePhaseAndTheRunningNeo4jStage() {
        assertThat(ProgressBar.describe(snapshot(ImportPhase.NODES, "", 0.1)))
                .isEqualTo("1/4 Node import");
        assertThat(ProgressBar.describe(snapshot(ImportPhase.NODES, "Prepare node index", 1.0)))
                .isEqualTo("1/4 Node import (Prepare node index)");
        assertThat(ProgressBar.describe(snapshot(ImportPhase.LINKING, "Relationship <-- Relationship", 0.7)))
                .isEqualTo("3/4 Relationship linking (Relationship <-- Relationship)");
    }

    @Test
    void rendersBarPercentPhaseElapsedAndPhaseEta() {
        String line = ProgressBar.render(
                new ProgressSnapshot(ImportPhase.LINKING, "Node Degrees", 0.5, Duration.ofMinutes(10)),
                Duration.ofHours(5));

        assertThat(line)
                .startsWith("[" + "#".repeat(15) + "-".repeat(15) + "]")
                .contains(" 50.0%")
                .contains("3/4 Relationship linking (Node Degrees)")
                .contains("elapsed 5h 00m")
                .endsWith("phase ETA 10m 00s");
    }

    @Test
    void printsLinesWhileRunningAndFinalStateOnClose() {
        var out = new ByteArrayOutputStream();
        var tracker = new ProgressTracker(100);
        tracker.add(50);

        new ProgressBar(tracker, new PrintStream(out), false, Duration.ofMinutes(1)).start().close();

        assertThat(out.toString(StandardCharsets.UTF_8).lines()).isNotEmpty().allSatisfy(line -> assertThat(line).contains(" 50.0%"));
    }

    @Test
    void overwritesTheLineInInteractiveMode() {
        var out = new ByteArrayOutputStream();
        var tracker = new ProgressTracker(100);

        new ProgressBar(tracker, new PrintStream(out), true, Duration.ofMinutes(1)).start().close();

        String output = out.toString(StandardCharsets.UTF_8);
        assertThat(output).startsWith("\r[").endsWith("\n");
    }

    private static ProgressSnapshot snapshot(ImportPhase phase, String stage, double fraction) {
        return new ProgressSnapshot(phase, stage, fraction, Duration.ofMinutes(1));
    }
}
