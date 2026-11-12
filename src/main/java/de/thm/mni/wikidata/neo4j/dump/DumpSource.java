package de.thm.mni.wikidata.neo4j.dump;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

public class DumpSource {

    private static final int BUFFER_SIZE = 1 << 20;

    private final Path file;
    private final Compression compression;

    public DumpSource(Path file, Compression compression) {
        this.file = file;
        this.compression = compression.resolve(file);
    }

    public LineChunkReader open() throws IOException {
        InputStream raw = Files.newInputStream(file);
        try {
            InputStream decompressed = decompress(raw);
            return new LineChunkReader(
                    new BufferedReader(new InputStreamReader(decompressed, StandardCharsets.UTF_8), BUFFER_SIZE));
        } catch (IOException | RuntimeException e) {
            raw.close();
            throw e;
        }
    }

    private InputStream decompress(InputStream raw) throws IOException {
        return switch (compression) {
            case GZIP -> new GZIPInputStream(raw, BUFFER_SIZE);
            case BZIP2 -> new BZip2CompressorInputStream(raw, true);
            case NONE, AUTO -> raw;
        };
    }
}
