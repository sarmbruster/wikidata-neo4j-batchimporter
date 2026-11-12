package de.thm.mni.wikidata.neo4j.neo4j;

import de.thm.mni.wikidata.neo4j.dump.DumpSource;
import de.thm.mni.wikidata.neo4j.dump.LineChunkReader;
import de.thm.mni.wikidata.neo4j.model.Entity;
import de.thm.mni.wikidata.neo4j.parse.EntityParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.neo4j.batchimport.api.InputIterable;
import org.neo4j.batchimport.api.InputIterator;
import org.neo4j.batchimport.api.input.InputChunk;
import org.neo4j.batchimport.api.input.InputEntityVisitor;

final class DumpInputIterable<R> implements InputIterable {

    private final DumpSource source;
    private final int chunkSize;
    private final EntityParser parser;
    private final Function<Entity, List<R>> mapper;
    private final BiConsumer<R, InputEntityVisitor> visit;

    DumpInputIterable(
            DumpSource source,
            int chunkSize,
            EntityParser parser,
            Function<Entity, List<R>> mapper,
            BiConsumer<R, InputEntityVisitor> visit) {
        this.source = source;
        this.chunkSize = chunkSize;
        this.parser = parser;
        this.mapper = mapper;
        this.visit = visit;
    }

    @Override
    public InputIterator iterator() {
        try {
            return new DumpIterator(source.open());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open dump " + source.file(), e);
        }
    }

    private final class DumpIterator implements InputIterator {

        private final LineChunkReader reader;

        DumpIterator(LineChunkReader reader) {
            this.reader = reader;
        }

        @Override
        public InputChunk newChunk() {
            return new DumpChunk();
        }

        @Override
        public boolean next(InputChunk chunk) throws IOException {
            List<byte[]> lines = reader.readChunk(chunkSize);
            if (lines.isEmpty()) {
                return false;
            }
            ((DumpChunk) chunk).load(lines);
            return true;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }

    private final class DumpChunk implements InputChunk {

        private List<byte[]> lines = List.of();
        private int nextLine;
        private final Queue<R> pending = new ArrayDeque<>();

        void load(List<byte[]> lines) {
            this.lines = lines;
            this.nextLine = 0;
            this.pending.clear();
        }

        @Override
        public boolean next(InputEntityVisitor visitor) throws IOException {
            while (pending.isEmpty()) {
                if (nextLine >= lines.size()) {
                    return false;
                }
                parser.parse(lines.get(nextLine++)).map(mapper).ifPresent(pending::addAll);
            }
            visit.accept(pending.poll(), visitor);
            visitor.endOfEntity();
            return true;
        }

        @Override
        public void close() {}
    }
}
