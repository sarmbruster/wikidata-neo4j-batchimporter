package de.thm.mni.wikidata.neo4j;

import de.thm.mni.wikidata.neo4j.dump.DumpSource;
import de.thm.mni.wikidata.neo4j.neo4j.ImporterRunner;
import de.thm.mni.wikidata.neo4j.neo4j.WikidataInput;
import de.thm.mni.wikidata.neo4j.progress.ProgressBar;
import de.thm.mni.wikidata.neo4j.progress.ProgressTracker;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.neo4j.internal.batchimport.input.BadCollector;
import org.neo4j.internal.batchimport.input.Collectors;

public final class WikidataImport implements Importer {

    private static final Duration INTERACTIVE_INTERVAL = Duration.ofMillis(500);
    private static final Duration LOG_INTERVAL = Duration.ofSeconds(30);

    private final PrintStream neo4jOutput;
    private final PrintStream barOutput;
    private final boolean interactive;

    public WikidataImport(PrintStream neo4jOutput, PrintStream barOutput, boolean interactive) {
        this.neo4jOutput = neo4jOutput;
        this.barOutput = barOutput;
        this.interactive = interactive;
    }

    @Override
    public ImportResult run(ImportRequest request) throws IOException {
        Files.createDirectories(request.neo4jHome());
        createParentDirectories(request.errorLog());
        var tracker = new ProgressTracker(2 * Files.size(request.jsonFile()));
        var source = new DumpSource(request.jsonFile(), request.compression(), tracker::add);
        var input = new WikidataInput(source, request.chunkSize());
        try (OutputStream errorLog = Files.newOutputStream(request.errorLog());
                var collector = Collectors.badCollector(errorLog, BadCollector.UNLIMITED_TOLERANCE)) {
            if (request.progress()) {
                try (var bar = new ProgressBar(
                                tracker, barOutput, interactive, interactive ? INTERACTIVE_INTERVAL : LOG_INTERVAL)
                        .start()) {
                    ImporterRunner.run(input, request.neo4jHome(), collector, null);
                }
            } else {
                ImporterRunner.run(input, request.neo4jHome(), collector, neo4jOutput);
            }
            return new ImportResult(collector.badEntries());
        }
    }

    private static void createParentDirectories(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
