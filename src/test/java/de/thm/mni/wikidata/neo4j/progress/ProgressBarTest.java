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
    void givesNoEstimateBeforeThereIsEnoughData() {
        assertThat(ProgressBar.eta(0.0, Duration.ofMinutes(5))).isEqualTo("--");
        assertThat(ProgressBar.eta(0.3, Duration.ofSeconds(1))).isEqualTo("--");
    }

    @Test
    void namesThePhaseByFraction() {
        assertThat(ProgressBar.phase(0.0)).isEqualTo("Nodes");
        assertThat(ProgressBar.phase(0.49)).isEqualTo("Nodes");
        assertThat(ProgressBar.phase(0.5)).isEqualTo("Relationships");
        assertThat(ProgressBar.phase(0.99)).isEqualTo("Relationships");
        assertThat(ProgressBar.phase(1.0)).isEqualTo("Finishing");
    }

    @Test
    void rendersBarPercentElapsedAndEta() {
        String line = ProgressBar.render(0.5, Duration.ofMinutes(10));

        assertThat(line)
                .startsWith("[" + "#".repeat(15) + "-".repeat(15) + "]")
                .contains(" 50.0%")
                .contains("Relationships")
                .contains("elapsed 10m 00s")
                .endsWith("ETA 10m 00s");
    }

    @Test
    void saysFinalizingInsteadOfEtaWhenAllBytesAreRead() {
        String line = ProgressBar.render(1.0, Duration.ofHours(2));

        assertThat(line).contains("100.0%").contains("Finishing").endsWith("finalizing in neo4j");
    }

    @Test
    void printsLinesWhileRunningAndFinalStateOnClose() {
        var out = new ByteArrayOutputStream();
        var tracker = new ProgressTracker(100);
        tracker.add(100);

        new ProgressBar(tracker, new PrintStream(out), false, Duration.ofMinutes(1)).start().close();

        assertThat(out.toString(StandardCharsets.UTF_8).lines()).isNotEmpty().allSatisfy(line -> assertThat(line).contains("100.0%"));
    }

    @Test
    void overwritesTheLineInInteractiveMode() {
        var out = new ByteArrayOutputStream();
        var tracker = new ProgressTracker(100);

        new ProgressBar(tracker, new PrintStream(out), true, Duration.ofMinutes(1)).start().close();

        String output = out.toString(StandardCharsets.UTF_8);
        assertThat(output).startsWith("\r[").endsWith("\n");
    }
}
