package de.thm.mni.wikidata.neo4j.neo4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.LongSupplier;
import org.neo4j.configuration.ToolingMemoryCalculations;
import org.neo4j.io.ByteUnit;
import org.neo4j.io.os.OsBeanUtil;

/**
 * Memory budget of the batch importer outside the JVM heap.
 *
 * <p>Neo4j's default takes 90 % of the free physical memory, which on Linux excludes the file system cache
 * ({@code MemFree}). After reading a large dump that value is small and relationship linking runs one type
 * per round. A percentage here refers to {@code MemAvailable} instead.
 */
public final class OffHeapMemory {

    public static final String DEFAULT = "90%";
    private static final Path MEMINFO = Path.of("/proc/meminfo");

    private OffHeapMemory() {
    }

    /**
     * @param value absolute size such as {@code 40g} or a percentage such as {@code 90%} of the available memory
     *     minus the JVM heap
     * @throws IllegalArgumentException if the value cannot be parsed
     */
    public static long resolve(String value) {
        return resolve(value, OffHeapMemory::availableMemory, Runtime.getRuntime().maxMemory());
    }

    static long resolve(String value, LongSupplier availableMemory, long maxHeap) {
        String trimmed = value.trim();
        if (trimmed.endsWith("%")) {
            int percent = parsePercent(trimmed);
            return new ToolingMemoryCalculations(ToolingMemoryCalculations.NO_MONITOR)
                    .calculateMaxAvailableOffHeapMemoryFromPercent(percent, availableMemory.getAsLong(), maxHeap);
        }
        long bytes;
        try {
            bytes = ByteUnit.parse(trimmed);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Not a memory size: " + value, e);
        }
        if (bytes <= 0) {
            throw new IllegalArgumentException("Memory size must be positive: " + value);
        }
        return bytes;
    }

    /** {@code MemAvailable} on Linux, otherwise the free physical memory as reported by the JVM. */
    public static long availableMemory() {
        return memAvailable(readMeminfo()).orElseGet(OsBeanUtil::getFreePhysicalMemory);
    }

    static OptionalLong memAvailable(List<String> meminfo) {
        for (String line : meminfo) {
            if (line.startsWith("MemAvailable:")) {
                String[] parts = line.split("\\s+");
                return OptionalLong.of(Long.parseLong(parts[1]) * 1024);
            }
        }
        return OptionalLong.empty();
    }

    private static List<String> readMeminfo() {
        if (!Files.isReadable(MEMINFO)) {
            return List.of();
        }
        try {
            return Files.readAllLines(MEMINFO);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int parsePercent(String value) {
        int percent;
        try {
            percent = Integer.parseInt(value.substring(0, value.length() - 1).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a percentage: " + value, e);
        }
        if (percent < 1 || percent > 99) {
            throw new IllegalArgumentException("Percentage must be between 1 and 99: " + value);
        }
        return percent;
    }
}
