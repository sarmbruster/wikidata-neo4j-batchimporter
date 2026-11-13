package de.thm.mni.wikidata.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import de.thm.mni.wikidata.neo4j.cli.WikidataImportCommand;
import de.thm.mni.wikidata.neo4j.neo4j.Neo4jHome;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WikidataImportEndToEndTest {

    @TempDir
    Path tmp;

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @Test
    void importsTheDumpFixtureIntoANonEmptyDatabase() {
        Path home = tmp.resolve("db");

        int exitCode = run(Fixtures.path(Fixtures.DUMP_100), home);

        assertThat(exitCode).as(err.toString()).isZero();
        assertThat(Neo4jHome.count(home, "MATCH (n) RETURN count(n) AS c")).isPositive();
        assertThat(Neo4jHome.count(home, "MATCH (n:Entity:Item) RETURN count(n) AS c")).isEqualTo(99);
        assertThat(Neo4jHome.count(home, "MATCH (n:Claim) RETURN count(n) AS c")).isPositive();
        assertThat(Neo4jHome.count(home, "MATCH (:Item {id:'Q31'})-[r]->(:Item) RETURN count(r) AS c")).isPositive();
        assertThat(tmp.resolve("db.errors.log")).exists();
        assertThat(out.toString()).contains("Neo4j database created");
    }

    @Test
    void importsMixedDumpWithRelationshipTypesFromPropertyLabels() throws Exception {
        Path home = tmp.resolve("mixed");

        int exitCode = run(Fixtures.path(Fixtures.MIXED), home);

        assertThat(exitCode).as(err.toString()).isZero();
        assertThat(Neo4jHome.count(home, "MATCH (n) RETURN count(n) AS c")).isEqualTo(19);
        assertThat(Neo4jHome.count(home, "MATCH ()-[r:INSTANCE_OF]->() RETURN count(r) AS c")).isEqualTo(1);
        assertThat(Files.readString(tmp.resolve("mixed.errors.log"))).contains("Q404404");
        assertThat(out.toString()).contains("1 relationships could not be resolved");
    }

    @Test
    void showsProgressBarInsteadOfNeo4jOutputWithProgressFlag() {
        Path home = tmp.resolve("progress");
        var neo4jOutput = new ByteArrayOutputStream();
        var barOutput = new ByteArrayOutputStream();
        var importer = new WikidataImport(new PrintStream(neo4jOutput), new PrintStream(barOutput), false);

        int exitCode = WikidataImportCommand.commandLine(importer)
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true))
                .execute(Fixtures.path(Fixtures.MIXED).toString(), home.toString(), "--progress");

        assertThat(exitCode).as(err.toString()).isZero();
        assertThat(neo4jOutput.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(barOutput.toString(StandardCharsets.UTF_8))
                .contains("100.0%")
                .contains("finalizing in neo4j");
        assertThat(Neo4jHome.count(home, "MATCH (n) RETURN count(n) AS c")).isEqualTo(19);
    }

    @Test
    void writesStagesNeo4jMessagesAndSummaryToTheLogFile() throws Exception {
        Path home = tmp.resolve("logged");
        Path logFile = tmp.resolve("import.log");

        int exitCode = run(Fixtures.path(Fixtures.MIXED), home, "--log-file=" + logFile);

        assertThat(exitCode).as(err.toString()).isZero();
        assertThat(Files.readString(logFile))
                .contains("INFO  Importing " + Fixtures.path(Fixtures.MIXED))
                .contains("Node import COMPLETED")
                .contains("IMPORT DONE")
                .contains("Import finished in")
                .contains("1 relationships could not be resolved");
    }

    @Test
    void keepsNeo4jStagesInTheLogFileWhenTheProgressBarIsShown() throws Exception {
        Path home = tmp.resolve("logged-progress");
        Path logFile = tmp.resolve("progress.log");
        var neo4jOutput = new ByteArrayOutputStream();
        var importer = new WikidataImport(
                new PrintStream(neo4jOutput), new PrintStream(new ByteArrayOutputStream()), false);

        int exitCode = WikidataImportCommand.commandLine(importer)
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true))
                .execute(Fixtures.path(Fixtures.MIXED).toString(), home.toString(), "--progress",
                        "--log-file=" + logFile);

        assertThat(exitCode).as(err.toString()).isZero();
        assertThat(neo4jOutput.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(Files.readString(logFile)).contains("Node import COMPLETED").contains("IMPORT DONE");
    }

    @Test
    void writesTheFailureWithStackTraceToTheLogFile() throws Exception {
        Path dump = Files.writeString(tmp.resolve("broken.json"), "[\n{\"type\":\"item\",\"id\":\n]\n");
        Path logFile = tmp.resolve("failed.log");

        int exitCode = run(dump, tmp.resolve("failed"), "--log-file=" + logFile);

        assertThat(exitCode).isNotZero();
        assertThat(Files.readString(logFile))
                .contains("ERROR Import failed after")
                .contains("Invalid entity JSON")
                .doesNotContain("Import finished");
    }

    private int run(Path dump, Path home, String... options) {
        String[] args = new String[options.length + 2];
        args[0] = dump.toString();
        args[1] = home.toString();
        System.arraycopy(options, 0, args, 2, options.length);
        return WikidataImportCommand.commandLine(new WikidataImport(null, null, false))
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true))
                .execute(args);
    }
}
