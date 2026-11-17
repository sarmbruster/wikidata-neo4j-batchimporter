package de.thm.mni.wikidata.neo4j;

import static org.neo4j.io.ByteUnit.bytesToString;

import de.thm.mni.wikidata.neo4j.dump.DumpSource;
import de.thm.mni.wikidata.neo4j.neo4j.ImporterRunner;
import de.thm.mni.wikidata.neo4j.neo4j.LoggingImportMonitor;
import de.thm.mni.wikidata.neo4j.neo4j.OffHeapMemory;
import de.thm.mni.wikidata.neo4j.neo4j.WikidataInput;
import de.thm.mni.wikidata.neo4j.progress.ProgressBar;
import de.thm.mni.wikidata.neo4j.progress.ProgressTracker;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.neo4j.batchimport.api.Monitor;
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
        var tracker = new ProgressTracker(Files.size(request.jsonFile()));
        var source = new DumpSource(request.jsonFile(), request.compression(), tracker::add);
        var input = new WikidataInput(source, request.chunkSize());
        try (var log = ImportLog.open(request.logFile());
                OutputStream errorLog = Files.newOutputStream(request.errorLog());
                var collector = Collectors.badCollector(errorLog, BadCollector.UNLIMITED_TOLERANCE)) {
            log.info("Importing " + request.jsonFile() + " (" + source.compression() + ", "
                    + Files.size(request.jsonFile()) + " bytes) into " + request.neo4jHome());
            log.info("Chunk size " + request.chunkSize() + ", error log " + request.errorLog());
            log.info("Off-heap memory for neo4j " + bytesToString(request.maxOffHeapMemory()) + " (available "
                    + bytesToString(OffHeapMemory.availableMemory()) + ", max heap "
                    + bytesToString(Runtime.getRuntime().maxMemory()) + ")");
            var monitor = new LoggingImportMonitor(warning -> {
                log.warn(warning);
                barOutput.println((interactive ? System.lineSeparator() : "") + "WARNING: " + warning);
            });
            long start = System.nanoTime();
            try {
                if (request.progress()) {
                    try (var bar = new ProgressBar(
                                    tracker, barOutput, interactive, interactive ? INTERACTIVE_INTERVAL : LOG_INTERVAL)
                            .start()) {
                        ImporterRunner.run(
                                input,
                                request.neo4jHome(),
                                collector,
                                options(request, log.tee(null), log, tracker, monitor));
                    }
                } else {
                    ImporterRunner.run(
                            input,
                            request.neo4jHome(),
                            collector,
                            options(request, log.tee(neo4jOutput), log, tracker, monitor));
                }
            } catch (IOException | RuntimeException e) {
                log.error("Import failed after " + elapsedSince(start), e);
                throw e;
            }
            var result = new ImportResult(collector.badEntries());
            log.info("Import finished in " + elapsedSince(start) + ", " + result.unresolvedRelationships()
                    + " relationships could not be resolved");
            return result;
        }
    }

    private static ImporterRunner.Options options(
            ImportRequest request, PrintStream progress, ImportLog log, ProgressTracker tracker, Monitor monitor) {
        return new ImporterRunner.Options(
                progress, log.neo4jLogProvider(), tracker, monitor, request.maxOffHeapMemory());
    }

    private static String elapsedSince(long startNanos) {
        return ProgressBar.format(Duration.ofNanos(System.nanoTime() - startNanos));
    }

    private static void createParentDirectories(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
