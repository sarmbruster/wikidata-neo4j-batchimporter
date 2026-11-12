package de.thm.mni.wikidata.neo4j.cli;

import de.thm.mni.wikidata.neo4j.ImportRequest;
import de.thm.mni.wikidata.neo4j.ImportResult;
import de.thm.mni.wikidata.neo4j.Importer;
import de.thm.mni.wikidata.neo4j.dump.Compression;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@Command(
        name = "wikidata-import",
        mixinStandardHelpOptions = true,
        description = "Imports a Wikidata JSON dump into a new Neo4j database using the batch importer.")
public class WikidataImportCommand implements Callable<Integer> {

    static final int DEFAULT_CHUNK_SIZE = 1000;

    private final Importer importer;

    @Spec
    CommandSpec spec;

    @Parameters(index = "0", paramLabel = "<json-file>", description = "Wikidata JSON dump (.json, .gz or .bz2).")
    Path jsonFile;

    @Parameters(index = "1", paramLabel = "<pfad>", description = "Target directory, must not exist or be empty.")
    Path target;

    @Option(
            names = "--compression",
            defaultValue = "AUTO",
            description = "Compression of the dump: ${COMPLETION-CANDIDATES}. AUTO decides by file extension. "
                    + "Default: ${DEFAULT-VALUE}.")
    Compression compression;

    @Option(
            names = "--error-log",
            paramLabel = "<datei>",
            description = "File for relationships that cannot be resolved. Default: <pfad>.errors.log.")
    Path errorLog;

    @Option(
            names = "--chunk-size",
            paramLabel = "<zeilen>",
            defaultValue = "" + DEFAULT_CHUNK_SIZE,
            description = "Number of dump lines read per chunk. Default: ${DEFAULT-VALUE}.")
    int chunkSize;

    public WikidataImportCommand(Importer importer) {
        this.importer = importer;
    }

    public static CommandLine commandLine(Importer importer) {
        return new CommandLine(new WikidataImportCommand(importer)).setCaseInsensitiveEnumValuesAllowed(true);
    }

    @Option(
            names = "--progress",
            description = "Show a progress bar with the estimated remaining time on stderr instead of the "
                    + "neo4j progress output. The estimate covers reading the dump, the final steps inside neo4j "
                    + "come on top.")
    boolean progress;

    @Override
    public Integer call() throws IOException {
        validate();
        var request = new ImportRequest(jsonFile, compression, target, errorLog(), chunkSize, progress);
        try {
            ImportResult result = importer.run(request);
            report(result);
            return 0;
        } catch (IOException | UncheckedIOException e) {
            spec.commandLine().getErr().println("Import failed: " + e.getMessage());
            return 1;
        }
    }

    private void report(ImportResult result) {
        var out = spec.commandLine().getOut();
        out.println("Neo4j database created in " + target + " (database 'neo4j').");
        if (result.unresolvedRelationships() > 0) {
            out.println(result.unresolvedRelationships() + " relationships could not be resolved, see " + errorLog());
        }
    }

    public Path jsonFile() {
        return jsonFile;
    }

    public Path target() {
        return target;
    }

    public Compression compression() {
        return compression;
    }

    public Path errorLog() {
        return errorLog != null ? errorLog : target.resolveSibling(target.getFileName() + ".errors.log");
    }

    public boolean progress() {
        return progress;
    }

    public int chunkSize() {
        return chunkSize;
    }

    private void validate() throws IOException {
        if (!Files.isRegularFile(jsonFile)) {
            fail("Dump file does not exist or is not a file: " + jsonFile);
        }
        if (chunkSize < 1) {
            fail("--chunk-size must be at least 1, was " + chunkSize);
        }
        if (Files.exists(target)) {
            if (!Files.isDirectory(target)) {
                fail("Target exists and is not a directory: " + target);
            }
            try (Stream<Path> entries = Files.list(target)) {
                if (entries.findAny().isPresent()) {
                    fail("Target directory is not empty: " + target);
                }
            }
        }
    }

    private void fail(String message) {
        throw new ParameterException(spec.commandLine(), message);
    }
}
