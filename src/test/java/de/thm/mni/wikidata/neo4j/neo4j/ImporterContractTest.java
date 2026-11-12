package de.thm.mni.wikidata.neo4j.neo4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.kernel.impl.scheduler.JobSchedulerFactory.createInitialisedScheduler;
import static org.neo4j.storageengine.api.TransactionIdStore.BASE_TX_ID;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.batchimport.api.AdditionalInitialIds;
import org.neo4j.batchimport.api.Configuration;
import org.neo4j.batchimport.api.InputIterable;
import org.neo4j.batchimport.api.InputIterator;
import org.neo4j.batchimport.api.Monitor;
import org.neo4j.batchimport.api.input.Collector;
import org.neo4j.batchimport.api.input.Group;
import org.neo4j.batchimport.api.input.IdType;
import org.neo4j.batchimport.api.input.Input;
import org.neo4j.batchimport.api.input.InputChunk;
import org.neo4j.batchimport.api.input.InputEntityVisitor;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.internal.batchimport.DefaultAdditionalIds;
import org.neo4j.internal.batchimport.input.BadCollector;
import org.neo4j.internal.batchimport.input.Collectors;
import org.neo4j.internal.batchimport.input.Groups;
import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.io.layout.Neo4jLayout;
import org.neo4j.io.pagecache.context.CursorContextFactory;
import org.neo4j.io.pagecache.context.FixedVersionContextSupplier;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.kernel.impl.index.schema.IndexImporterFactoryImpl;
import org.neo4j.kernel.impl.transaction.log.files.TransactionLogInitializer;
import org.neo4j.logging.NullLogProvider;
import org.neo4j.logging.internal.SimpleLogService;
import org.neo4j.memory.EmptyMemoryTracker;
import org.neo4j.storageengine.api.StorageEngineFactory;

class ImporterContractTest {

    private static final String DATABASE = GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

    @TempDir
    Path home;

    @Test
    void readsNodesAndRelationshipsExactlyOnceAndAllNodesFirst() throws Exception {
        var input = new RecordingInput(
                List.of("A", "B", "C"),
                List.of(new Edge("A", "B"), new Edge("B", "C")));

        runImport(input, Collectors.badCollector(new ByteArrayOutputStream(), BadCollector.UNLIMITED_TOLERANCE));

        assertThat(input.nodeIteratorsOpened()).hasValue(1);
        assertThat(input.relationshipIteratorsOpened()).hasValue(1);
        assertThat(input.events())
                .startsWith(RecordingInput.NODE)
                .endsWith(RecordingInput.RELATIONSHIP)
                .containsSubsequence(RecordingInput.NODE, RecordingInput.RELATIONSHIP)
                .satisfies(events -> assertThat(events.lastIndexOf(RecordingInput.NODE))
                        .isLessThan(events.indexOf(RecordingInput.RELATIONSHIP)));
    }

    @Test
    void resolvesRelationshipToNodeThatComesLaterInTheInput() throws Exception {
        var input = new RecordingInput(List.of("A", "B"), List.of(new Edge("A", "Z-later"), new Edge("A", "B")));
        input.nodes.add("Z-later");

        runImport(input, Collectors.badCollector(new ByteArrayOutputStream(), BadCollector.UNLIMITED_TOLERANCE));

        assertThat(countRelationships()).isEqualTo(2);
    }

    @Test
    void writesRelationshipWithMissingTargetToErrorLogAndContinues() throws Exception {
        var input = new RecordingInput(List.of("A", "B"), List.of(new Edge("A", "B"), new Edge("A", "missing")));
        var errorLog = new ByteArrayOutputStream();
        var collector = Collectors.badCollector(errorLog, BadCollector.UNLIMITED_TOLERANCE);

        runImport(input, collector);

        assertThat(countRelationships()).isEqualTo(1);
        assertThat(collector.badEntries()).isEqualTo(1);
        assertThat(errorLog.toString(StandardCharsets.UTF_8)).contains("missing");
    }

    private void runImport(Input input, Collector collector) throws IOException {
        Config config = Config.newBuilder()
                .set(GraphDatabaseSettings.neo4j_home, home)
                .build();
        DatabaseLayout layout = Neo4jLayout.of(config).databaseLayout(DATABASE);
        try (var scheduler = createInitialisedScheduler();
                var fileSystem = new DefaultFileSystemAbstraction()) {
            var importer = StorageEngineFactory.selectStorageEngine(config)
                    .batchImporter(
                            layout,
                            fileSystem,
                            PageCacheTracer.NULL,
                            Configuration.DEFAULT,
                            new SimpleLogService(NullLogProvider.getInstance()),
                            null,
                            false,
                            DefaultAdditionalIds.EMPTY,
                            config,
                            new Monitor() {},
                            scheduler,
                            collector,
                            TransactionLogInitializer.getLogFilesInitializer(),
                            new IndexImporterFactoryImpl(),
                            EmptyMemoryTracker.INSTANCE,
                            new CursorContextFactory(PageCacheTracer.NULL, new FixedVersionContextSupplier(BASE_TX_ID)));
            importer.doImport(input);
        }
    }

    private long countRelationships() {
        DatabaseManagementService dbms = new DatabaseManagementServiceBuilder(home).build();
        try {
            GraphDatabaseService db = dbms.database(DATABASE);
            try (var tx = db.beginTx()) {
                return tx.execute("MATCH ()-[r]->() RETURN count(r) AS c").<Long>columnAs("c").next();
            }
        } finally {
            dbms.shutdown();
        }
    }

    record Edge(String from, String to) {}

    static final class RecordingInput implements Input {

        static final String NODE = "node";
        static final String RELATIONSHIP = "relationship";

        final List<String> nodes;
        final List<Edge> relationships;
        private final Queue<String> events = new ConcurrentLinkedQueue<>();
        private final AtomicInteger nodeIteratorsOpened = new AtomicInteger();
        private final AtomicInteger relationshipIteratorsOpened = new AtomicInteger();
        private final Groups groups = new Groups();
        private final Group group = groups.getOrCreate(null);

        RecordingInput(List<String> nodes, List<Edge> relationships) {
            this.nodes = new ArrayList<>(nodes);
            this.relationships = new ArrayList<>(relationships);
        }

        AtomicInteger nodeIteratorsOpened() {
            return nodeIteratorsOpened;
        }

        AtomicInteger relationshipIteratorsOpened() {
            return relationshipIteratorsOpened;
        }

        List<String> events() {
            return List.copyOf(events);
        }

        @Override
        public InputIterable nodes(Collector collector) {
            return () -> {
                nodeIteratorsOpened.incrementAndGet();
                return new ListIterator<>(nodes, (entity, visitor) -> {
                    events.add(NODE);
                    visitor.id(entity, group);
                    visitor.labels(new String[] {"Entity"});
                    visitor.property("name", entity);
                });
            };
        }

        @Override
        public InputIterable relationships(Collector collector) {
            return () -> {
                relationshipIteratorsOpened.incrementAndGet();
                return new ListIterator<>(relationships, (edge, visitor) -> {
                    events.add(RELATIONSHIP);
                    visitor.startId(edge.from(), group);
                    visitor.endId(edge.to(), group);
                    visitor.type("LINKS");
                });
            };
        }

        @Override
        public IdType idType() {
            return IdType.STRING;
        }

        @Override
        public org.neo4j.batchimport.api.input.ReadableGroups groups() {
            return groups;
        }

        @Override
        public Estimates validateAndEstimate(org.neo4j.batchimport.api.input.PropertySizeCalculator calculator) {
            return Input.knownEstimates(nodes.size(), relationships.size(), nodes.size(), 0, nodes.size() * 16L, 0, 1);
        }
    }

    interface Visit<T> {
        void accept(T entity, InputEntityVisitor visitor);
    }

    static final class ListIterator<T> implements InputIterator {

        private final List<T> items;
        private final Visit<T> visit;
        private int next;

        ListIterator(List<T> items, Visit<T> visit) {
            this.items = items;
            this.visit = visit;
        }

        @Override
        public InputChunk newChunk() {
            return new ListChunk();
        }

        @Override
        public synchronized boolean next(InputChunk chunk) {
            if (next >= items.size()) {
                return false;
            }
            var listChunk = (ListChunk) chunk;
            listChunk.current = items.get(next++);
            listChunk.pending = true;
            return true;
        }

        @Override
        public void close() {}

        private final class ListChunk implements InputChunk {

            private T current;
            private boolean pending;

            @Override
            public boolean next(InputEntityVisitor visitor) throws IOException {
                if (!pending) {
                    return false;
                }
                pending = false;
                visit.accept(current, visitor);
                visitor.endOfEntity();
                return true;
            }

            @Override
            public void close() {}
        }
    }
}
