package de.thm.mni.wikidata.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.logging.NullLogProvider;

class ImportLogTest {

    @TempDir
    Path tmp;

    @Test
    void withoutLogFilePassesTheConsoleThroughAndDiscardsMessages() throws Exception {
        var console = new PrintStream(new ByteArrayOutputStream());

        try (var log = ImportLog.open(null)) {
            log.info("ignored");

            assertThat(log.tee(console)).isSameAs(console);
            assertThat(log.tee(null)).isNull();
            assertThat(log.neo4jLogProvider()).isSameAs(NullLogProvider.getInstance());
        }
    }

    @Test
    void writesTimestampedMessagesAndNeo4jLogToTheFile() throws Exception {
        Path file = tmp.resolve("logs/import.log");

        try (var log = ImportLog.open(file)) {
            log.info("starting");
            log.neo4jLogProvider().getLog(ImportLogTest.class).info("from neo4j");
            log.error("broken", new IllegalStateException("boom"));
        }

        assertThat(Files.readAllLines(file))
                .anySatisfy(line -> assertThat(line).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+0000 INFO  starting"))
                .anySatisfy(line -> assertThat(line).contains("from neo4j"))
                .anySatisfy(line -> assertThat(line).contains("ERROR broken"))
                .anySatisfy(line -> assertThat(line).contains("IllegalStateException: boom"));
    }

    @Test
    void teeWritesToConsoleAndFile() throws Exception {
        Path file = tmp.resolve("tee.log");
        var console = new ByteArrayOutputStream();

        try (var log = ImportLog.open(file)) {
            log.tee(new PrintStream(console, true, StandardCharsets.UTF_8)).println("stage done");
        }

        assertThat(console.toString(StandardCharsets.UTF_8)).contains("stage done");
        assertThat(Files.readString(file)).contains("stage done");
    }
}
