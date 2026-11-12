package de.thm.mni.wikidata.neo4j;

import de.thm.mni.wikidata.neo4j.dump.Compression;
import de.thm.mni.wikidata.neo4j.dump.DumpSource;
import de.thm.mni.wikidata.neo4j.model.Entity;
import de.thm.mni.wikidata.neo4j.parse.EntityParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Fixtures {

    public static final String DUMP_100 = "/wikidata-100.json";
    public static final String MIXED = "/mixed.json";

    private Fixtures() {
    }

    public static Path path(String resource) {
        try {
            return Path.of(Fixtures.class.getResource(resource).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    public static List<String> records(String resource, int max) {
        List<String> records = new ArrayList<>();
        try (var reader = new DumpSource(path(resource), Compression.AUTO).open()) {
            List<byte[]> chunk;
            while (records.size() < max && !(chunk = reader.readChunk(100)).isEmpty()) {
                chunk.forEach(record -> records.add(new String(record, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return records.size() > max ? records.subList(0, max) : records;
    }

    public static Map<String, Entity> entities(String resource) {
        var parser = new EntityParser();
        Map<String, Entity> entities = new LinkedHashMap<>();
        for (String record : records(resource, Integer.MAX_VALUE)) {
            Entity entity = parser.parse(record).orElseThrow();
            entities.put(entity.id(), entity);
        }
        return entities;
    }
}
