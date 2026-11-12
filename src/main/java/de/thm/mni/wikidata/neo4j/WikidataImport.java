package de.thm.mni.wikidata.neo4j;

import de.thm.mni.wikidata.neo4j.dump.DumpSource;
import de.thm.mni.wikidata.neo4j.neo4j.ImporterRunner;
import de.thm.mni.wikidata.neo4j.neo4j.WikidataInput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.neo4j.internal.batchimport.input.BadCollector;
import org.neo4j.internal.batchimport.input.Collectors;

public final class WikidataImport implements Importer {

    private final PrintStream progress;

    public WikidataImport(PrintStream progress) {
        this.progress = progress;
    }

    @Override
    public ImportResult run(ImportRequest request) throws IOException {
        Files.createDirectories(request.neo4jHome());
        createParentDirectories(request.errorLog());
        var source = new DumpSource(request.jsonFile(), request.compression());
        var input = new WikidataInput(source, request.chunkSize());
        try (OutputStream errorLog = Files.newOutputStream(request.errorLog());
                var collector = Collectors.badCollector(errorLog, BadCollector.UNLIMITED_TOLERANCE)) {
            ImporterRunner.run(input, request.neo4jHome(), collector, progress);
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
