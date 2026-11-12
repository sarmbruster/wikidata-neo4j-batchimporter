package de.thm.mni.wikidata.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import de.thm.mni.wikidata.neo4j.neo4j.Neo4jHome;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WikidataImportScriptIT {

    private static final Path SCRIPT = Path.of("bin", "wikidata-import.sh").toAbsolutePath();

    @TempDir
    Path tmp;

    @Test
    void importsBzip2DumpIntoNonEmptyDatabase() throws Exception {
        Path dump = tmp.resolve("dump.json.bz2");
        try (OutputStream out = new BZip2CompressorOutputStream(Files.newOutputStream(dump))) {
            Files.copy(Fixtures.path(Fixtures.MIXED), out);
        }
        Path home = tmp.resolve("db");

        Process process = new ProcessBuilder(SCRIPT.toString(), dump.toString(), home.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);

        assertThat(finished).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(Neo4jHome.count(home, "MATCH (n) RETURN count(n) AS c")).isEqualTo(19);
        assertThat(Neo4jHome.count(home, "MATCH ()-[r:INSTANCE_OF]->() RETURN count(r) AS c")).isEqualTo(1);
        assertThat(tmp.resolve("db.errors.log")).exists();
    }

    @Test
    void failsWithUsageErrorForMissingDump() throws Exception {
        Process process = new ProcessBuilder(
                        SCRIPT.toString(), tmp.resolve("missing.json").toString(), tmp.resolve("db").toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor(2, TimeUnit.MINUTES);

        assertThat(process.exitValue()).isEqualTo(2);
        assertThat(output).contains("Dump file does not exist");
    }
}
