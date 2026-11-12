package de.thm.mni.wikidata.neo4j.dump;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LineChunkReaderTest {

    @Test
    void stripsArrayBracketsTrailingCommasAndWhitespace() throws IOException {
        String dump = "[\n  {\"id\":\"Q1\"},  \n{\"id\":\"Q2\"},\r\n\n{\"id\":\"Q3\"}\n]\n";

        assertThat(readAll(dump, 8, 100)).containsExactly("{\"id\":\"Q1\"}", "{\"id\":\"Q2\"}", "{\"id\":\"Q3\"}");
    }

    @Test
    void returnsLastRecordWithoutTrailingNewline() throws IOException {
        assertThat(readAll("[\n{\"id\":\"Q1\"},\n{\"id\":\"Q2\"}", 8, 100))
                .containsExactly("{\"id\":\"Q1\"}", "{\"id\":\"Q2\"}");
    }

    @Test
    void handlesRecordsLongerThanTheBuffer() throws IOException {
        String longRecord = "{\"label\":\"" + "x".repeat(10_000) + "\"}";

        assertThat(readAll("[\n" + longRecord + ",\n{\"id\":\"Q2\"}\n]\n", 16, 100))
                .containsExactly(longRecord, "{\"id\":\"Q2\"}");
    }

    @Test
    void keepsMultiByteUtf8CharactersAcrossBufferBoundaries() throws IOException {
        String record = "{\"label\":\"Köln – 東京 – 😀\"}";

        assertThat(readAll("[\n" + record + ",\n" + record + "\n]\n", 5, 100)).containsExactly(record, record);
    }

    @Test
    void limitsChunksToTheRequestedNumberOfRecords() throws IOException {
        var reader = reader("[\n{},\n{},\n{}\n]\n", 4);

        assertThat(reader.readChunk(2)).hasSize(2);
        assertThat(reader.readChunk(2)).hasSize(1);
        assertThat(reader.readChunk(2)).isEmpty();
    }

    @Test
    void emptyInputHasNoRecords() throws IOException {
        assertThat(readAll("", 8, 10)).isEmpty();
        assertThat(readAll("[\n]\n", 8, 10)).isEmpty();
    }

    private static List<String> readAll(String dump, int bufferSize, int chunkSize) throws IOException {
        var reader = reader(dump, bufferSize);
        List<String> records = new ArrayList<>();
        List<byte[]> chunk;
        while (!(chunk = reader.readChunk(chunkSize)).isEmpty()) {
            chunk.forEach(record -> records.add(new String(record, StandardCharsets.UTF_8)));
        }
        return records;
    }

    private static LineChunkReader reader(String dump, int bufferSize) {
        return new LineChunkReader(new ByteArrayInputStream(dump.getBytes(StandardCharsets.UTF_8)), bufferSize);
    }
}
