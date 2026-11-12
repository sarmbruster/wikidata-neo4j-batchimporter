package de.thm.mni.wikidata.neo4j.progress;

import java.util.concurrent.atomic.LongAdder;

public final class ProgressTracker {

    private final long totalBytes;
    private final LongAdder processedBytes = new LongAdder();

    public ProgressTracker(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public void add(long bytes) {
        processedBytes.add(bytes);
    }

    public double fraction() {
        if (totalBytes <= 0) {
            return 1.0;
        }
        return Math.min(1.0, (double) processedBytes.sum() / totalBytes);
    }
}
