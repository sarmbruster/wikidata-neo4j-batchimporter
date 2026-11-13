package de.thm.mni.wikidata.neo4j.neo4j;

import static org.neo4j.kernel.impl.scheduler.JobSchedulerFactory.createInitialisedScheduler;
import static org.neo4j.storageengine.api.TransactionIdStore.BASE_TX_ID;

import de.thm.mni.wikidata.neo4j.progress.StageListener;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import org.neo4j.batchimport.api.Configuration;
import org.neo4j.batchimport.api.Monitor;
import org.neo4j.batchimport.api.input.Collector;
import org.neo4j.batchimport.api.input.Input;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.internal.batchimport.BatchImporterFactory;
import org.neo4j.internal.batchimport.DefaultAdditionalIds;
import org.neo4j.internal.batchimport.staging.ExecutionMonitor;
import org.neo4j.internal.batchimport.staging.ExecutionMonitors;
import org.neo4j.internal.batchimport.staging.MultiExecutionMonitor;
import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.io.layout.Neo4jLayout;
import org.neo4j.io.pagecache.context.CursorContextFactory;
import org.neo4j.io.pagecache.context.FixedVersionContextSupplier;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.kernel.impl.index.schema.IndexImporterFactoryImpl;
import org.neo4j.kernel.impl.transaction.log.EmptyLogTailMetadata;
import org.neo4j.kernel.impl.transaction.log.files.TransactionLogInitializer;
import org.neo4j.logging.InternalLogProvider;
import org.neo4j.logging.NullLogProvider;
import org.neo4j.logging.internal.SimpleLogService;
import org.neo4j.memory.EmptyMemoryTracker;

public final class ImporterRunner {

    public static final String DATABASE = GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

    private ImporterRunner() {
    }

    public static void run(Input input, Path neo4jHome, Collector collector, PrintStream progress)
            throws IOException {
        run(input, neo4jHome, collector, progress, NullLogProvider.getInstance(), StageListener.NONE);
    }

    /**
     * @param progress receives Neo4j's own stage report, may be {@code null}
     * @param stages receives the stage events of the importer
     */
    public static void run(
            Input input,
            Path neo4jHome,
            Collector collector,
            PrintStream progress,
            InternalLogProvider logProvider,
            StageListener stages)
            throws IOException {
        Config config = Config.newBuilder()
                .set(GraphDatabaseSettings.neo4j_home, neo4jHome.toAbsolutePath())
                .build();
        var layout = Neo4jLayout.of(config).databaseLayout(DATABASE);
        try (var scheduler = createInitialisedScheduler();
                var fileSystem = new DefaultFileSystemAbstraction()) {
            var importer = BatchImporterFactory.withHighestPriority()
                    .instantiate(
                            layout,
                            fileSystem,
                            PageCacheTracer.NULL,
                            Configuration.DEFAULT,
                            new SimpleLogService(logProvider),
                            executionMonitor(progress, stages),
                            DefaultAdditionalIds.EMPTY,
                            new EmptyLogTailMetadata(config),
                            config,
                            Monitor.NO_MONITOR,
                            scheduler,
                            new ReadableIdCollector(collector),
                            TransactionLogInitializer.getLogFilesInitializer(),
                            new IndexImporterFactoryImpl(),
                            EmptyMemoryTracker.INSTANCE,
                            new CursorContextFactory(PageCacheTracer.NULL, new FixedVersionContextSupplier(BASE_TX_ID)));
            importer.doImport(input);
        }
    }

    private static ExecutionMonitor executionMonitor(PrintStream progress, StageListener stages) {
        var stageMonitor = new StageProgressMonitor(stages);
        if (progress == null) {
            return stageMonitor;
        }
        return new MultiExecutionMonitor(
                ExecutionMonitors.defaultVisible(Monitor.NO_MONITOR, progress, System.err), stageMonitor);
    }
}
