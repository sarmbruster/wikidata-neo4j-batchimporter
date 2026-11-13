package de.thm.mni.wikidata.neo4j.neo4j;

import de.thm.mni.wikidata.neo4j.progress.ImportPhase;
import de.thm.mni.wikidata.neo4j.progress.StageListener;
import java.util.Set;
import org.neo4j.common.DependencyResolver;
import org.neo4j.internal.batchimport.CountGroupsStage;
import org.neo4j.internal.batchimport.DataImporter;
import org.neo4j.internal.batchimport.DataStatistics;
import org.neo4j.internal.batchimport.NodeDegreeCountStage;
import org.neo4j.internal.batchimport.RelationshipGroupStage;
import org.neo4j.internal.batchimport.ScanAndCacheGroupsStage;
import org.neo4j.internal.batchimport.SparseNodeFirstRelationshipStage;
import org.neo4j.internal.batchimport.staging.StageExecution;
import org.neo4j.internal.batchimport.staging.ExecutionMonitor;
import org.neo4j.internal.batchimport.staging.Step;
import org.neo4j.internal.batchimport.stats.Keys;
import org.neo4j.internal.batchimport.store.BatchingNeoStores;

/**
 * Follows the stages of the batch importer and reports the progress of the phases after the dump has been read.
 * Phase boundaries, goals and progress calculation mirror Neo4j's {@code HumanUnderstandableExecutionMonitor},
 * which prints the same progress as dots.
 */
final class StageProgressMonitor implements ExecutionMonitor {

    private static final Set<String> UNTRACKED_STAGES =
            Set.of(RelationshipGroupStage.NAME, SparseNodeFirstRelationshipStage.NAME, ScanAndCacheGroupsStage.NAME);

    private final StageListener listener;
    private DependencyResolver dependencies;
    private ImportPhase phase = ImportPhase.NODES;
    private long goal;
    private long completedInPhase;

    StageProgressMonitor(StageListener listener) {
        this.listener = listener;
    }

    @Override
    public void initialize(DependencyResolver dependencyResolver) {
        this.dependencies = dependencyResolver;
    }

    @Override
    public void start(StageExecution execution) {
        String stage = execution.getStageName();
        ImportPhase next = phaseOf(stage, phase);
        if (next != phase) {
            phase = next;
            goal = phase.readsDump() ? 0 : goalOf(phase);
            completedInPhase = 0;
        }
        boolean dumpPass = stage.equals(DataImporter.NODE_IMPORT_NAME)
                || stage.equals(DataImporter.RELATIONSHIP_IMPORT_NAME);
        // the name of a linking stage carries the relationship types of its round, e.g. "... 4-6/6"
        listener.stageStarted(phase, dumpPass ? "" : execution.name());
    }

    @Override
    public void check(StageExecution execution) {
        if (tracked(execution)) {
            report(completedInPhase + progressOf(execution));
        }
    }

    @Override
    public void end(StageExecution execution, long totalTimeMillis) {
        if (tracked(execution)) {
            completedInPhase += progressOf(execution);
            report(completedInPhase);
        }
    }

    @Override
    public void done(boolean successful, long totalTimeMillis, String additionalInformation) {}

    @Override
    public long checkIntervalMillis() {
        return 1000;
    }

    static ImportPhase phaseOf(String stage, ImportPhase current) {
        return switch (stage) {
            case DataImporter.NODE_IMPORT_NAME -> ImportPhase.NODES;
            case DataImporter.RELATIONSHIP_IMPORT_NAME -> ImportPhase.RELATIONSHIPS;
            case NodeDegreeCountStage.NAME -> ImportPhase.LINKING;
            case CountGroupsStage.NAME -> ImportPhase.POST_PROCESSING;
            default -> current;
        };
    }

    private long goalOf(ImportPhase phase) {
        var neoStores = dependencies.resolveDependency(BatchingNeoStores.class);
        var statistics = dependencies.resolveDependency(DataStatistics.class);
        long relationshipIds = neoStores.getRelationshipStore().getIdGenerator().getHighId();
        if (phase == ImportPhase.LINKING) {
            // node degrees over all relationship ids, then forward and backward linking of both ends
            return relationshipIds + 4 * statistics.getRelationshipCount();
        }
        long groups = neoStores.getTemporaryRelationshipGroupStore().getIdGenerator().getHighId();
        // count, write and link groups, then node and relationship counts
        return 3 * groups + statistics.getNodeCount() + relationshipIds;
    }

    private void report(long progress) {
        if (goal > 0) {
            // several linking rounds (when the types do not fit into memory at once) can exceed the goal
            listener.phaseProgress(Math.min(1.0, (double) progress / goal));
        }
    }

    private boolean tracked(StageExecution execution) {
        return !phase.readsDump() && !UNTRACKED_STAGES.contains(execution.getStageName());
    }

    private static long progressOf(StageExecution execution) {
        Step<?> last = null;
        for (Step<?> step : execution.steps()) {
            var progress = step.stats().stat(Keys.progress);
            if (progress != null) {
                return progress.asLong();
            }
            last = step;
        }
        if (last == null) {
            return 0;
        }
        return last.stats().stat(Keys.done_batches).asLong() * execution.getConfig().batchSize();
    }
}
