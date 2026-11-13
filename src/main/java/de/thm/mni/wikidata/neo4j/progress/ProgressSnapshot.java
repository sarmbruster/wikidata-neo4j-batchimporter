package de.thm.mni.wikidata.neo4j.progress;

import java.time.Duration;

public record ProgressSnapshot(ImportPhase phase, String stage, double fraction, Duration phaseElapsed) {}
