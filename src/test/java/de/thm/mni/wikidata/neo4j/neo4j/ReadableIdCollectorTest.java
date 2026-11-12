package de.thm.mni.wikidata.neo4j.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.neo4j.internal.batchimport.input.BadCollector;
import org.neo4j.internal.batchimport.input.Collectors;
import org.neo4j.internal.batchimport.input.Groups;

class ReadableIdCollectorTest {

    @Test
    void writesWikidataKeysInsteadOfEncodedIdsToTheErrorLog() {
        var errorLog = new ByteArrayOutputStream();
        var group = new Groups().getOrCreate(null);
        var collector = new ReadableIdCollector(Collectors.badCollector(errorLog, BadCollector.UNLIMITED_TOLERANCE));

        collector.collectBadRelationship(
                InputIds.encode("Q100"), group, "NAMED_AFTER", InputIds.encode("Q404404"), group,
                InputIds.encode("Q404404"));
        collector.close();

        assertThat(collector.badEntries()).isEqualTo(1);
        assertThat(errorLog.toString(StandardCharsets.UTF_8))
                .contains("Q100")
                .contains("Q404404")
                .doesNotContain(Long.toString(InputIds.encode("Q404404")));
    }
}
