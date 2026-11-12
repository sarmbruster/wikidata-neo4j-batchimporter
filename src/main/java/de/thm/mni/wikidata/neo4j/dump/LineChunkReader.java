package de.thm.mni.wikidata.neo4j.dump;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits the decompressed dump into records of raw UTF-8 bytes. Only the line splitting happens under the lock,
 * decoding is left to the JSON parser in the calling thread.
 */
public class LineChunkReader implements Closeable {

    static final int DEFAULT_BUFFER_SIZE = 1 << 20;

    private final InputStream in;
    private byte[] buffer;
    private int position;
    private int limit;
    private boolean endOfStream;

    LineChunkReader(InputStream in) {
        this(in, DEFAULT_BUFFER_SIZE);
    }

    LineChunkReader(InputStream in, int bufferSize) {
        this.in = in;
        this.buffer = new byte[bufferSize];
    }

    public synchronized List<byte[]> readChunk(int maxRecords) throws IOException {
        List<byte[]> records = new ArrayList<>(Math.min(maxRecords, 1024));
        while (records.size() < maxRecords) {
            byte[] record = nextRecord();
            if (record == null) {
                break;
            }
            records.add(record);
        }
        return records;
    }

    private byte[] nextRecord() throws IOException {
        while (true) {
            int newline = indexOfNewline();
            if (newline >= 0) {
                int lineStart = position;
                position = newline + 1;
                byte[] record = toRecord(lineStart, newline);
                if (record != null) {
                    return record;
                }
            } else if (endOfStream) {
                int lineStart = position;
                position = limit;
                return lineStart < limit ? toRecord(lineStart, limit) : null;
            } else {
                fill();
            }
        }
    }

    private int indexOfNewline() {
        for (int i = position; i < limit; i++) {
            if (buffer[i] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private void fill() throws IOException {
        int remaining = limit - position;
        if (remaining == buffer.length) {
            buffer = Arrays.copyOf(buffer, buffer.length * 2);
        } else if (position > 0) {
            System.arraycopy(buffer, position, buffer, 0, remaining);
        }
        position = 0;
        limit = remaining;
        int read = in.read(buffer, limit, buffer.length - limit);
        if (read < 0) {
            endOfStream = true;
        } else {
            limit += read;
        }
    }

    private byte[] toRecord(int from, int to) {
        while (from < to && isWhitespace(buffer[from])) {
            from++;
        }
        while (to > from && isWhitespace(buffer[to - 1])) {
            to--;
        }
        if (to > from && buffer[to - 1] == ',') {
            to--;
        }
        int length = to - from;
        if (length == 0 || (length == 1 && (buffer[from] == '[' || buffer[from] == ']'))) {
            return null;
        }
        return Arrays.copyOfRange(buffer, from, to);
    }

    private static boolean isWhitespace(byte b) {
        return b == ' ' || b == '\t' || b == '\r' || b == '\n';
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
