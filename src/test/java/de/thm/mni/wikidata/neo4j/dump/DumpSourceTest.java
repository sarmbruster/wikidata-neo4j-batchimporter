package de.thm.mni.wikidata.neo4j.dump;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DumpSourceTest {

    private static Path plain;
    private static List<String> expected;

    @TempDir
    Path tmp;

    @BeforeAll
    static void loadFixture() throws Exception {
        plain = Path.of(DumpSourceTest.class.getResource("/wikidata-100.json").toURI());
        expected = readAll(new DumpSource(plain, Compression.AUTO), 1000);
    }

    @Test
    void readsOneRecordPerLineWithoutArrayBracketsAndCommas() {
        assertThat(expected).hasSize(99);
        assertThat(expected.getFirst()).startsWith("{\"type\":\"item\",\"id\":\"Q31\"");
        assertThat(expected).allSatisfy(record -> assertThat(record).startsWith("{").endsWith("}"));
    }

    @Test
    void readsGzipLikePlain() throws Exception {
        Path gz = compress("wikidata.json.gz", GZIPOutputStream::new);

        assertThat(readAll(new DumpSource(gz, Compression.AUTO), 1000)).isEqualTo(expected);
    }

    @Test
    void readsBzip2LikePlain() throws Exception {
        Path bz2 = compress("wikidata.json.bz2", BZip2CompressorOutputStream::new);

        assertThat(readAll(new DumpSource(bz2, Compression.AUTO), 1000)).isEqualTo(expected);
    }

    @Test
    void explicitCompressionOverridesFileExtension() throws Exception {
        Path gzWithoutExtension = compress("dump.dat", GZIPOutputStream::new);
        Path plainWithGzExtension = tmp.resolve("plain.json.gz");
        Files.copy(plain, plainWithGzExtension);

        assertThat(readAll(new DumpSource(gzWithoutExtension, Compression.GZIP), 1000)).isEqualTo(expected);
        assertThat(readAll(new DumpSource(plainWithGzExtension, Compression.NONE), 1000)).isEqualTo(expected);
    }

    @Test
    void reportsEveryRawByteOfTheFileThatIsRead() throws Exception {
        Path gz = compress("wikidata.json.gz", GZIPOutputStream::new);
        Path bz2 = compress("wikidata.json.bz2", BZip2CompressorOutputStream::new);

        for (Path file : List.of(plain, gz, bz2)) {
            var counted = new java.util.concurrent.atomic.AtomicLong();
            readAll(new DumpSource(file, Compression.AUTO, counted::addAndGet), 1000);

            assertThat(counted).as(file.getFileName().toString()).hasValue(Files.size(file));
        }
    }

    @Test
    void resolvesCompressionByFileExtension() {
        assertThat(Compression.AUTO.resolve(Path.of("a.json"))).isEqualTo(Compression.NONE);
        assertThat(Compression.AUTO.resolve(Path.of("a.json.gz"))).isEqualTo(Compression.GZIP);
        assertThat(Compression.AUTO.resolve(Path.of("A.JSON.BZ2"))).isEqualTo(Compression.BZIP2);
        assertThat(Compression.GZIP.resolve(Path.of("a.json"))).isEqualTo(Compression.GZIP);
    }

    @Test
    void canBeOpenedRepeatedlyWithIdenticalContent() throws Exception {
        var source = new DumpSource(plain, Compression.AUTO);

        assertThat(readAll(source, 1000)).isEqualTo(readAll(source, 1000));
    }

    @Test
    void deliversChunksOfRequestedSizeAndEmptyChunkAtEnd() throws Exception {
        try (var reader = new DumpSource(plain, Compression.AUTO).open()) {
            assertThat(reader.readChunk(40)).hasSize(40);
            assertThat(reader.readChunk(40)).hasSize(40);
            assertThat(reader.readChunk(40)).hasSize(19);
            assertThat(reader.readChunk(40)).isEmpty();
        }
    }

    @Test
    void concurrentReadersGetEveryRecordExactlyOnce() throws Exception {
        var received = new ConcurrentLinkedQueue<String>();
        var executor = Executors.newFixedThreadPool(4);
        try (var reader = new DumpSource(plain, Compression.AUTO).open()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(executor.submit(() -> {
                    List<String> chunk;
                    while (!(chunk = reader.readChunk(7)).isEmpty()) {
                        received.addAll(chunk);
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdown();
        }

        assertThat(received).containsExactlyInAnyOrderElementsOf(expected);
    }

    private Path compress(String name, StreamWrapper wrapper) throws IOException {
        Path target = tmp.resolve(name);
        try (OutputStream out = wrapper.wrap(Files.newOutputStream(target))) {
            Files.copy(plain, out);
        }
        return target;
    }

    private static List<String> readAll(DumpSource source, int chunkSize) throws IOException {
        List<String> records = new ArrayList<>();
        try (var reader = source.open()) {
            List<String> chunk;
            while (!(chunk = reader.readChunk(chunkSize)).isEmpty()) {
                records.addAll(chunk);
            }
        }
        return records;
    }

    @FunctionalInterface
    private interface StreamWrapper {
        OutputStream wrap(OutputStream out) throws IOException;
    }
}
