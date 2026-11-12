package de.thm.mni.wikidata.neo4j.dump;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LineChunkReader implements Closeable {

    private final BufferedReader reader;

    LineChunkReader(BufferedReader reader) {
        this.reader = reader;
    }

    public synchronized List<String> readChunk(int maxLines) throws IOException {
        List<String> chunk = new ArrayList<>(maxLines);
        while (chunk.size() < maxLines) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            String record = toRecord(line);
            if (record != null) {
                chunk.add(record);
            }
        }
        return chunk;
    }

    private static String toRecord(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.equals("[") || trimmed.equals("]")) {
            return null;
        }
        return trimmed.endsWith(",") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
