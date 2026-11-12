package de.thm.mni.wikidata.neo4j.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.neo4j.batchimport.api.InputIterable;
import org.neo4j.batchimport.api.InputIterator;
import org.neo4j.batchimport.api.input.Collector;
import org.neo4j.batchimport.api.input.Group;
import org.neo4j.batchimport.api.input.IdType;
import org.neo4j.batchimport.api.input.Input;
import org.neo4j.batchimport.api.input.InputChunk;
import org.neo4j.batchimport.api.input.InputEntityVisitor;
import org.neo4j.internal.batchimport.input.BadCollector;
import org.neo4j.internal.batchimport.input.Collectors;
import org.neo4j.internal.batchimport.input.Groups;

class ImporterContractTest {

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
        ImporterRunner.run(input, home, collector, null);
    }

    private long countRelationships() {
        return Neo4jHome.count(home, "MATCH ()-[r]->() RETURN count(r) AS c");
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
