package de.thm.mni.wikidata.neo4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import de.thm.mni.wikidata.neo4j.ImportRequest;
import de.thm.mni.wikidata.neo4j.ImportResult;
import de.thm.mni.wikidata.neo4j.dump.Compression;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class WikidataImportCommandTest {

    @TempDir
    Path tmp;

    private Path dump;
    private WikidataImportCommand command;
    private CommandLine commandLine;
    private StringWriter err;
    private StringWriter out;
    private List<ImportRequest> requests;

    @BeforeEach
    void setUp() throws Exception {
        dump = Path.of(getClass().getResource("/wikidata-100.json").toURI());
        requests = new ArrayList<>();
        command = new WikidataImportCommand(request -> {
            requests.add(request);
            return new ImportResult(0);
        });
        err = new StringWriter();
        out = new StringWriter();
        commandLine = new CommandLine(command).setErr(new PrintWriter(err)).setOut(new PrintWriter(out));
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
    }

    @Test
    void acceptsDumpAndNonExistingTargetWithDefaults() {
        Path target = tmp.resolve("db");

        int exitCode = execute(dump, target);

        assertThat(exitCode).isZero();
        assertThat(command.jsonFile()).isEqualTo(dump);
        assertThat(command.target()).isEqualTo(target);
        assertThat(command.compression()).isEqualTo(Compression.AUTO);
        assertThat(command.chunkSize()).isEqualTo(1000);
        assertThat(command.errorLog()).isEqualTo(tmp.resolve("db.errors.log"));
    }

    @Test
    void acceptsEmptyExistingTargetDirectory() throws Exception {
        Path target = Files.createDirectory(tmp.resolve("db"));

        assertThat(execute(dump, target)).isZero();
    }

    @Test
    void readsOptions() {
        Path target = tmp.resolve("db");
        Path errorLog = tmp.resolve("custom.log");

        int exitCode = execute(
                dump, target, "--compression=bzip2", "--error-log=" + errorLog, "--chunk-size=50");

        assertThat(exitCode).isZero();
        assertThat(command.compression()).isEqualTo(Compression.BZIP2);
        assertThat(command.errorLog()).isEqualTo(errorLog);
        assertThat(command.chunkSize()).isEqualTo(50);
    }

    @Test
    void passesTheParsedOptionsToTheImporter() {
        Path target = tmp.resolve("db");

        execute(dump, target, "--compression=gzip", "--chunk-size=7");

        assertThat(requests).containsExactly(
                new ImportRequest(dump, Compression.GZIP, target, tmp.resolve("db.errors.log"), 7, false, null));
    }

    @Test
    void passesTheLogFileToTheImporterAndReportsIt() {
        Path logFile = tmp.resolve("import.log");

        execute(dump, tmp.resolve("db"), "--log-file=" + logFile);

        assertThat(command.logFile()).isEqualTo(logFile);
        assertThat(requests.getFirst().logFile()).isEqualTo(logFile);
        assertThat(out.toString()).contains("Log written to " + logFile);
    }

    @Test
    void writesNoLogFileByDefault() {
        execute(dump, tmp.resolve("db"));

        assertThat(requests.getFirst().logFile()).isNull();
        assertThat(out.toString()).doesNotContain("Log written");
    }

    @Test
    void enablesProgressBarWithFlag() {
        execute(dump, tmp.resolve("db"), "--progress");

        assertThat(command.progress()).isTrue();
        assertThat(requests.getFirst().progress()).isTrue();
    }

    @Test
    void progressBarIsOffByDefault() {
        execute(dump, tmp.resolve("db"));

        assertThat(command.progress()).isFalse();
    }

    @Test
    void doesNotRunTheImporterForInvalidArguments() {
        execute(tmp.resolve("missing.json"), tmp.resolve("db"));

        assertThat(requests).isEmpty();
    }

    @Test
    void reportsUnresolvedRelationships() {
        var failing = new CommandLine(new WikidataImportCommand(request -> new ImportResult(3)))
                .setOut(new PrintWriter(out));

        int exitCode = failing.execute(dump.toString(), tmp.resolve("db").toString());

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("3 relationships could not be resolved").contains("db.errors.log");
    }

    @Test
    void returnsExitCodeOneWhenTheImportFails() {
        var failing = new CommandLine(new WikidataImportCommand(request -> {
                    throw new java.io.IOException("disk full");
                }))
                .setErr(new PrintWriter(err));

        int exitCode = failing.execute(dump.toString(), tmp.resolve("db").toString());

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString()).contains("Import failed: disk full");
    }

    @Test
    void rejectsMissingDumpFile() {
        int exitCode = execute(tmp.resolve("missing.json"), tmp.resolve("db"));

        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).contains("Dump file does not exist");
    }

    @Test
    void rejectsNonEmptyTargetDirectory() throws Exception {
        Path target = Files.createDirectory(tmp.resolve("db"));
        Files.writeString(target.resolve("file.txt"), "x");

        int exitCode = execute(dump, target);

        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).contains("Target directory is not empty");
    }

    @Test
    void rejectsTargetThatIsAFile() throws Exception {
        Path target = Files.writeString(tmp.resolve("db"), "x");

        int exitCode = execute(dump, target);

        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).contains("not a directory");
    }

    @Test
    void rejectsNonPositiveChunkSize() {
        int exitCode = execute(dump, tmp.resolve("db"), "--chunk-size=0");

        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).contains("--chunk-size must be at least 1");
    }

    @Test
    void rejectsUnknownCompression() {
        int exitCode = execute(dump, tmp.resolve("db"), "--compression=zip");

        assertThat(exitCode).isEqualTo(2);
    }

    @Test
    void rejectsMissingArguments() {
        assertThat(commandLine.execute(dump.toString())).isEqualTo(2);
    }

    private int execute(Path jsonFile, Path target, String... options) {
        String[] args = new String[options.length + 2];
        args[0] = jsonFile.toString();
        args[1] = target.toString();
        System.arraycopy(options, 0, args, 2, options.length);
        return commandLine.execute(args);
    }
}
