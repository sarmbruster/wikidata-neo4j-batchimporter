package de.thm.mni.wikidata.neo4j.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import de.thm.mni.wikidata.neo4j.Fixtures;
import de.thm.mni.wikidata.neo4j.dump.Compression;
import de.thm.mni.wikidata.neo4j.dump.DumpSource;
import de.thm.mni.wikidata.neo4j.progress.ImportPhase;
import de.thm.mni.wikidata.neo4j.progress.StageListener;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.internal.batchimport.input.BadCollector;
import org.neo4j.internal.batchimport.input.Collectors;
import org.neo4j.logging.NullLogProvider;

class StageProgressMonitorTest {

    @TempDir
    Path home;

    @Test
    void mapsNeo4jStagesToPhases() {
        assertThat(StageProgressMonitor.phaseOf("Nodes", ImportPhase.NODES)).isEqualTo(ImportPhase.NODES);
        assertThat(StageProgressMonitor.phaseOf("Prepare node index", ImportPhase.NODES))
                .isEqualTo(ImportPhase.NODES);
        assertThat(StageProgressMonitor.phaseOf("Relationships", ImportPhase.NODES))
                .isEqualTo(ImportPhase.RELATIONSHIPS);
        assertThat(StageProgressMonitor.phaseOf("Node Degrees", ImportPhase.RELATIONSHIPS))
                .isEqualTo(ImportPhase.LINKING);
        assertThat(StageProgressMonitor.phaseOf("Relationship <-- Relationship", ImportPhase.LINKING))
                .isEqualTo(ImportPhase.LINKING);
        assertThat(StageProgressMonitor.phaseOf("Count groups", ImportPhase.LINKING))
                .isEqualTo(ImportPhase.POST_PROCESSING);
    }

    @Test
    void reportsAllPhasesInOrderAndCompletesThePhasesAfterReadingTheDump() throws Exception {
        var listener = new RecordingListener();

        ImporterRunner.run(
                new WikidataInput(new DumpSource(Fixtures.path(Fixtures.MIXED), Compression.AUTO), 1000),
                home,
                Collectors.badCollector(new ByteArrayOutputStream(), BadCollector.UNLIMITED_TOLERANCE),
                null,
                NullLogProvider.getInstance(),
                listener);

        assertThat(listener.phases)
                .containsExactly(
                        ImportPhase.NODES, ImportPhase.RELATIONSHIPS, ImportPhase.LINKING, ImportPhase.POST_PROCESSING);
        assertThat(listener.stages)
                .contains("Node Degrees")
                .doesNotContain("Nodes", "Relationships")
                .anySatisfy(stage -> assertThat(stage).matches("Relationship --> Relationship \\d+(-\\d+)?/\\d+"));
        assertThat(listener.progress).doesNotContainKeys(ImportPhase.NODES, ImportPhase.RELATIONSHIPS);
        assertThat(listener.progress.get(ImportPhase.LINKING)).isSorted().last().isEqualTo(1.0);
        assertThat(listener.progress.get(ImportPhase.POST_PROCESSING)).isSorted().last().isEqualTo(1.0);
    }

    private static final class RecordingListener implements StageListener {
        final List<ImportPhase> phases = new ArrayList<>();
        final List<String> stages = new ArrayList<>();
        final Map<ImportPhase, List<Double>> progress = new EnumMap<>(ImportPhase.class);
        private ImportPhase current;

        @Override
        public void stageStarted(ImportPhase phase, String stage) {
            if (phase != current) {
                phases.add(phase);
                current = phase;
            }
            if (!stage.isEmpty()) {
                stages.add(stage);
            }
        }

        @Override
        public void phaseProgress(double fraction) {
            progress.computeIfAbsent(current, phase -> new ArrayList<>()).add(fraction);
        }
    }
}
