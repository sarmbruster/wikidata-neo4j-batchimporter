package de.thm.mni.wikidata.neo4j.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoggingImportMonitorTest {

    private final List<String> warnings = new ArrayList<>();
    private final LoggingImportMonitor monitor = new LoggingImportMonitor(warnings::add);

    @Test
    void reportsInsufficientMemoryWithTheSuggestedDistribution() {
        monitor.insufficientAvailableMemory(14L << 30, 2L << 30, 1L << 30);

        assertThat(warnings).singleElement().asString()
                .contains("Free memory 1.000GiB")
                .contains("heap 2.000GiB")
                .contains("14.00GiB");
    }

    @Test
    void reportsHeapSizes() {
        monitor.abundantHeapSize(2L << 30, 16L << 30);
        monitor.insufficientHeapSize(2L << 30, 1L << 30);

        assertThat(warnings).hasSize(2);
        assertThat(warnings.getFirst()).contains("unnecessarily large").contains("16.00GiB");
        assertThat(warnings.getLast()).contains("too small").contains("1.000GiB");
    }

    @Test
    void ignoresPercentageUpdates() {
        monitor.started();
        monitor.percentageCompleted(50);
        monitor.completed(true);

        assertThat(warnings).isEmpty();
    }
}
