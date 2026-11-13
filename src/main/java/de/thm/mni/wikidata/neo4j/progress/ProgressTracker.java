package de.thm.mni.wikidata.neo4j.progress;

import java.time.Duration;
import java.util.concurrent.atomic.LongAdder;

/**
 * Combines the bytes read from the dump (two passes over the same file) with the stage events of the
 * batch importer. Node and relationship import are measured by the bytes read in their pass, the later
 * phases by the progress Neo4j reports for its own stages.
 */
public final class ProgressTracker implements StageListener {

    private final long passBytes;
    private final LongAdder readBytes = new LongAdder();
    private volatile State state;

    public ProgressTracker(long passBytes) {
        this.passBytes = passBytes;
        this.state = new State(ImportPhase.NODES, "", 0, System.nanoTime());
    }

    public void add(long bytes) {
        readBytes.add(bytes);
    }

    @Override
    public void stageStarted(ImportPhase phase, String stage) {
        State current = state;
        state = phase == current.phase()
                ? new State(phase, stage, current.fraction(), current.phaseStartNanos())
                : new State(phase, stage, 0, System.nanoTime());
    }

    @Override
    public void phaseProgress(double fraction) {
        State current = state;
        state = new State(current.phase(), current.stage(), clamp(fraction), current.phaseStartNanos());
    }

    public ProgressSnapshot snapshot() {
        State current = state;
        double fraction = current.phase().readsDump() ? passFraction(current.phase().ordinal()) : current.fraction();
        return new ProgressSnapshot(
                current.phase(),
                current.stage(),
                fraction,
                Duration.ofNanos(System.nanoTime() - current.phaseStartNanos()));
    }

    private double passFraction(int pass) {
        if (passBytes <= 0) {
            return 1.0;
        }
        return clamp((double) (readBytes.sum() - pass * passBytes) / passBytes);
    }

    private static double clamp(double fraction) {
        return Math.max(0, Math.min(1.0, fraction));
    }

    private record State(ImportPhase phase, String stage, double fraction, long phaseStartNanos) {}
}
