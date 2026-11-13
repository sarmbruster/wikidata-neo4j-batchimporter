package de.thm.mni.wikidata.neo4j.progress;

public interface StageListener {

    StageListener NONE = new StageListener() {
        @Override
        public void stageStarted(ImportPhase phase, String stage) {}

        @Override
        public void phaseProgress(double fraction) {}
    };

    void stageStarted(ImportPhase phase, String stage);

    /** Progress of the current phase between 0 and 1; only reported for phases that do not read the dump. */
    void phaseProgress(double fraction);
}
