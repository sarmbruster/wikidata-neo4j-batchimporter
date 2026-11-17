package de.thm.mni.wikidata.neo4j.neo4j;

import static org.neo4j.io.ByteUnit.bytesToString;

import java.util.function.Consumer;
import org.neo4j.batchimport.api.Monitor;

/** Passes the sanity checks of the batch importer, which neo4j-admin prints as warnings, to a consumer. */
public final class LoggingImportMonitor implements Monitor {

    private final Consumer<String> warnings;

    public LoggingImportMonitor(Consumer<String> warnings) {
        this.warnings = warnings;
    }

    @Override
    public void doubleRelationshipRecordUnitsEnabled() {
        warnings.accept("Double record units are used for all relationships");
    }

    @Override
    public void mayExceedNodeIdCapacity(long capacity, long estimatedCount) {
        warnings.accept("Estimated number of nodes " + estimatedCount
                + " may exceed the capacity " + capacity + " of the record format");
    }

    @Override
    public void mayExceedRelationshipIdCapacity(long capacity, long estimatedCount) {
        warnings.accept("Estimated number of relationships " + estimatedCount
                + " may exceed the capacity " + capacity + " of the record format");
    }

    @Override
    public void insufficientHeapSize(long optimalMinimalHeapSize, long heapSize) {
        warnings.accept("Heap size " + bytesToString(heapSize) + " may be too small for this import, suggested: "
                + bytesToString(optimalMinimalHeapSize));
    }

    @Override
    public void abundantHeapSize(long optimalMinimalHeapSize, long heapSize) {
        warnings.accept("Heap size " + bytesToString(heapSize) + " is unnecessarily large and leaves less memory "
                + "for the off-heap caches, suggested: " + bytesToString(optimalMinimalHeapSize));
    }

    @Override
    public void insufficientAvailableMemory(long estimatedCacheSize, long optimalMinimalHeapSize, long availableMemory) {
        warnings.accept("Free memory " + bytesToString(availableMemory) + " may not be sufficient for this import, "
                + "suggested: heap " + bytesToString(optimalMinimalHeapSize) + ", free memory besides the heap "
                + bytesToString(estimatedCacheSize));
    }
}
