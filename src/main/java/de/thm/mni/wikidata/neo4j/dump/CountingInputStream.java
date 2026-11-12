package de.thm.mni.wikidata.neo4j.dump;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.LongConsumer;

final class CountingInputStream extends FilterInputStream {

    private final LongConsumer onBytesRead;

    CountingInputStream(InputStream in, LongConsumer onBytesRead) {
        super(in);
        this.onBytesRead = onBytesRead;
    }

    @Override
    public int read() throws IOException {
        int value = super.read();
        if (value >= 0) {
            onBytesRead.accept(1);
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int count = super.read(buffer, offset, length);
        if (count > 0) {
            onBytesRead.accept(count);
        }
        return count;
    }
}
