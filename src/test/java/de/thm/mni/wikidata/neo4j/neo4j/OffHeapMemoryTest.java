package de.thm.mni.wikidata.neo4j.neo4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class OffHeapMemoryTest {

    @Test
    void parsesAbsoluteSizes() {
        assertThat(OffHeapMemory.resolve("40g")).isEqualTo(40L << 30);
        assertThat(OffHeapMemory.resolve("512m")).isEqualTo(512L << 20);
    }

    @Test
    void derivesPercentagesFromTheAvailableMemoryMinusTheHeap() {
        assertThat(OffHeapMemory.resolve("90%", () -> 32L << 30, 4L << 30)).isEqualTo(Math.round(0.9 * (28L << 30)));
    }

    @Test
    void assumesAtMostHalfOfTheAvailableMemoryForAnOversizedHeap() {
        assertThat(OffHeapMemory.resolve("50%", () -> 20L << 30, 16L << 30)).isEqualTo(5L << 30);
    }

    @Test
    void defaultsToAPositiveBudgetOnThisMachine() {
        assertThat(OffHeapMemory.resolve(OffHeapMemory.DEFAULT)).isPositive();
    }

    @Test
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> OffHeapMemory.resolve("lots")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OffHeapMemory.resolve("0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OffHeapMemory.resolve("0%")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OffHeapMemory.resolve("100%")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsMemAvailableInsteadOfMemFree() {
        var meminfo = List.of(
                "MemTotal:       61391400 kB", "MemFree:          731200 kB", "MemAvailable:   10476052 kB");

        assertThat(OffHeapMemory.memAvailable(meminfo)).hasValue(10476052L * 1024);
        assertThat(OffHeapMemory.memAvailable(List.of("MemTotal: 1 kB"))).isEmpty();
    }
}
