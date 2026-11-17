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

    /**
     * @param progress receives Neo4j's own stage report, may be {@code null}
     * @param stages receives the stage events of the importer
     * @param monitor receives the sanity checks of the importer, e.g. memory warnings
     * @param maxOffHeapMemory memory budget of the importer caches outside the heap, see {@link OffHeapMemory}
     */
    public record Options(
            PrintStream progress,
            InternalLogProvider logProvider,
            StageListener stages,
            Monitor monitor,
            long maxOffHeapMemory) {

        public static Options defaults() {
            return new Options(
                    null,
                    NullLogProvider.getInstance(),
                    StageListener.NONE,
                    Monitor.NO_MONITOR,
                    OffHeapMemory.resolve(OffHeapMemory.DEFAULT));
        }
    }

    public static void run(Input input, Path neo4jHome, Collector collector) throws IOException {
        run(input, neo4jHome, collector, Options.defaults());
    }

    public static void run(Input input, Path neo4jHome, Collector collector, Options options) throws IOException {
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
                            configuration(options.maxOffHeapMemory()),
                            new SimpleLogService(options.logProvider()),
                            executionMonitor(options),
                            DefaultAdditionalIds.EMPTY,
                            new EmptyLogTailMetadata(config),
                            config,
                            options.monitor(),
                            scheduler,
                            new ReadableIdCollector(collector),
                            TransactionLogInitializer.getLogFilesInitializer(),
                            new IndexImporterFactoryImpl(),
                            EmptyMemoryTracker.INSTANCE,
                            new CursorContextFactory(PageCacheTracer.NULL, new FixedVersionContextSupplier(BASE_TX_ID)));
            importer.doImport(input);
        }
    }

    private static Configuration configuration(long maxOffHeapMemory) {
        return new Configuration.Overridden(Configuration.DEFAULT) {
            @Override
            public long maxOffHeapMemory() {
                return maxOffHeapMemory;
            }
        };
    }

    private static ExecutionMonitor executionMonitor(Options options) {
        var stageMonitor = new StageProgressMonitor(options.stages());
        if (options.progress() == null) {
            return stageMonitor;
        }
        return new MultiExecutionMonitor(
                ExecutionMonitors.defaultVisible(options.monitor(), options.progress(), System.err), stageMonitor);
    }
}
