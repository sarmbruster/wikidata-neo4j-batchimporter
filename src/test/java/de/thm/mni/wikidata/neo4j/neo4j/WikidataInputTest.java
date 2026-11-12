package de.thm.mni.wikidata.neo4j.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import de.thm.mni.wikidata.neo4j.Fixtures;
import de.thm.mni.wikidata.neo4j.dump.Compression;
import de.thm.mni.wikidata.neo4j.dump.DumpSource;
import de.thm.mni.wikidata.neo4j.dump.LineChunkReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.batchimport.api.input.Collector;
import org.neo4j.internal.batchimport.input.BadCollector;
import org.neo4j.internal.batchimport.input.Collectors;

class WikidataInputTest {

    @TempDir
    Path home;

    @Test
    void importsMixedDumpWithForwardReferencesAndReadsTheFileTwice() throws Exception {
        var source = new CountingDumpSource(Fixtures.path(Fixtures.MIXED));
        var errorLog = new ByteArrayOutputStream();
        var collector = Collectors.badCollector(errorLog, BadCollector.UNLIMITED_TOLERANCE);

        ImporterRunner.run(new WikidataInput(source, 4), home, collector, null);

        assertThat(source.opened).hasValue(2);
        assertThat(Neo4jHome.count(home, "MATCH (n:Entity:Item) RETURN count(n) AS c")).isEqualTo(5);
        assertThat(Neo4jHome.count(home, "MATCH (n:Entity:Property) RETURN count(n) AS c")).isEqualTo(9);
        assertThat(Neo4jHome.count(home, "MATCH (n:Claim) RETURN count(n) AS c")).isEqualTo(5);
        assertThat(Neo4jHome.count(home, "MATCH ()-[r]->() RETURN count(r) AS c")).isEqualTo(10);
        assertThat(Neo4jHome.count(
                        home,
                        "MATCH (:Item {id:'Q100'})-[r:INSTANCE_OF {by:'P31', id:'Q100$1'}]->(:Item {id:'Q200'}) "
                                + "RETURN count(r) AS c"))
                .isEqualTo(1);
        assertThat(Neo4jHome.count(
                        home,
                        "MATCH (:Item {id:'Q300'})-[:CAPITAL]->(:Item {id:'Q100', label:'Berlin'}) "
                                + "RETURN count(*) AS c"))
                .isEqualTo(1);
        assertThat(Neo4jHome.count(
                        home,
                        "MATCH (:Item {id:'Q100'})-[:AREA]->(q:Claim:Quantity)-[:UNIT_TYPE]->(:Item {id:'Q712226'}) "
                                + "WHERE q.amount = '+891.8' AND q.unit IS NULL RETURN count(q) AS c"))
                .isEqualTo(1);
        assertThat(Neo4jHome.count(
                        home,
                        "MATCH (:Claim:GlobeCoordinate)-[:GLOBE_TYPE]->(:Item {id:'Q2'}) RETURN count(*) AS c"))
                .isEqualTo(1);
    }

    @Test
    void writesRelationshipToEntityMissingInTheDumpToTheErrorLog() throws Exception {
        var errorLog = new ByteArrayOutputStream();
        var collector = Collectors.badCollector(errorLog, BadCollector.UNLIMITED_TOLERANCE);

        ImporterRunner.run(
                new WikidataInput(new DumpSource(Fixtures.path(Fixtures.MIXED), Compression.AUTO), 1000),
                home,
                collector,
                null);

        assertThat(collector.badEntries()).isEqualTo(1);
        assertThat(errorLog.toString(StandardCharsets.UTF_8)).contains("Q404404");
    }

    @Test
    void writesRelationshipWithUnencodableTargetToTheErrorLogAndContinues() throws Exception {
        Path dump = home.resolve("invalid.json");
        java.nio.file.Files.writeString(dump, """
                [
                {"type":"item","id":"Q1","labels":{"en":{"language":"en","value":"one"}},"claims":{"P31":[{"mainsnak":{"snaktype":"value","property":"P31","datatype":"wikibase-item","datavalue":{"value":{"entity-type":"item","id":"Qbroken"},"type":"wikibase-entityid"}},"type":"statement","id":"Q1$1"}],"P279":[{"mainsnak":{"snaktype":"value","property":"P279","datatype":"wikibase-item","datavalue":{"value":{"entity-type":"item","id":"Q2"},"type":"wikibase-entityid"}},"type":"statement","id":"Q1$2"}]}},
                {"type":"item","id":"Q2","labels":{"en":{"language":"en","value":"two"}}}
                ]
                """);
        var errorLog = new ByteArrayOutputStream();
        var collector = Collectors.badCollector(errorLog, BadCollector.UNLIMITED_TOLERANCE);

        ImporterRunner.run(new WikidataInput(new DumpSource(dump, Compression.NONE), 1000), home, collector, null);

        assertThat(collector.badEntries()).isEqualTo(1);
        assertThat(errorLog.toString(StandardCharsets.UTF_8)).contains("Qbroken");
        assertThat(Neo4jHome.count(home, "MATCH (:Item {id:'Q1'})-[r]->(:Item {id:'Q2'}) RETURN count(r) AS c"))
                .isEqualTo(1);
    }

    @Test
    void storesTheStatementIdOnClaimNodes() throws Exception {
        var collector = Collectors.badCollector(new ByteArrayOutputStream(), BadCollector.UNLIMITED_TOLERANCE);

        ImporterRunner.run(
                new WikidataInput(new DumpSource(Fixtures.path(Fixtures.MIXED), Compression.AUTO), 1000),
                home,
                collector,
                null);

        assertThat(Neo4jHome.count(
                        home,
                        "MATCH (:Item {id:'Q100'})-[:AREA]->(c:Claim {id:'Q100$4'}) RETURN count(c) AS c"))
                .isEqualTo(1);
    }

    @Test
    void estimatesGrowWithFileSizeAndAreNeverZero() throws Exception {
        Path small = home.resolve("small.json");
        Path large = home.resolve("large.json");
        java.nio.file.Files.writeString(small, "[\n]\n");
        try (var file = new RandomAccessFile(large.toFile(), "rw")) {
            file.setLength(1L << 30);
        }

        var smallEstimates = WikidataInput.estimate(new DumpSource(small, Compression.NONE));
        var largeEstimates = WikidataInput.estimate(new DumpSource(large, Compression.NONE));
        var compressedEstimates = WikidataInput.estimate(new DumpSource(large, Compression.GZIP));

        assertThat(smallEstimates.numberOfNodes()).isPositive();
        assertThat(smallEstimates.numberOfRelationships()).isPositive();
        assertThat(largeEstimates.numberOfNodes()).isGreaterThan(smallEstimates.numberOfNodes());
        assertThat(compressedEstimates.numberOfNodes()).isGreaterThan(largeEstimates.numberOfNodes());
    }

    private static final class CountingDumpSource extends DumpSource {

        final AtomicInteger opened = new AtomicInteger();

        CountingDumpSource(Path file) {
            super(file, Compression.AUTO);
        }

        @Override
        public LineChunkReader open() throws IOException {
            opened.incrementAndGet();
            return super.open();
        }
    }
}
