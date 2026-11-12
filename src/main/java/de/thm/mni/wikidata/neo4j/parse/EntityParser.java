package de.thm.mni.wikidata.neo4j.parse;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import de.thm.mni.wikidata.neo4j.model.DataValue;
import de.thm.mni.wikidata.neo4j.model.DataValue.ClaimValue;
import de.thm.mni.wikidata.neo4j.model.DataValue.EntityRef;
import de.thm.mni.wikidata.neo4j.model.Entity;
import de.thm.mni.wikidata.neo4j.model.EntityType;
import de.thm.mni.wikidata.neo4j.model.Statement;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EntityParser {

    private static final String LANGUAGE = "en";
    private static final int MAX_ERROR_CONTEXT = 120;

    private final JsonFactory factory = new JsonFactory();

    public Optional<Entity> parse(String json) {
        try (JsonParser parser = factory.createParser(json)) {
            return parseEntity(parser);
        } catch (IOException e) {
            String context = json.length() > MAX_ERROR_CONTEXT ? json.substring(0, MAX_ERROR_CONTEXT) + "..." : json;
            throw new UncheckedIOException("Invalid entity JSON: " + context, e);
        }
    }

    private Optional<Entity> parseEntity(JsonParser p) throws IOException {
        if (p.nextToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected a JSON object");
        }
        String id = null;
        String type = null;
        String datatype = null;
        String label = "";
        List<String> descriptions = List.of();
        List<String> aliases = List.of();
        List<Statement> statements = List.of();
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            String field = p.currentName();
            p.nextToken();
            switch (field) {
                case "id" -> id = p.getText();
                case "type" -> type = p.getText();
                case "datatype" -> datatype = p.getText();
                case "labels" -> label = englishEntry(p, this::readText).orElse("");
                case "descriptions" ->
                    descriptions = englishEntry(p, this::readText).map(List::of).orElse(List.of());
                case "aliases" -> aliases = englishEntry(p, this::readTexts).orElse(List.of());
                case "claims" -> statements = readClaims(p);
                default -> p.skipChildren();
            }
        }
        Optional<EntityType> entityType = EntityType.fromJson(type);
        if (entityType.isEmpty() || id == null) {
            return Optional.empty();
        }
        return Optional.of(new Entity(id, entityType.get(), label, descriptions, aliases, datatype, statements));
    }

    private interface Reader<T> {
        T read(JsonParser p) throws IOException;
    }

    private <T> Optional<T> englishEntry(JsonParser p, Reader<T> reader) throws IOException {
        if (p.currentToken() != JsonToken.START_OBJECT) {
            p.skipChildren();
            return Optional.empty();
        }
        T result = null;
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            String language = p.currentName();
            p.nextToken();
            if (LANGUAGE.equals(language)) {
                result = reader.read(p);
            } else {
                p.skipChildren();
            }
        }
        return Optional.ofNullable(result);
    }

    private String readText(JsonParser p) throws IOException {
        if (p.currentToken() != JsonToken.START_OBJECT) {
            p.skipChildren();
            return null;
        }
        String value = null;
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            String field = p.currentName();
            p.nextToken();
            if ("value".equals(field)) {
                value = p.getText();
            } else {
                p.skipChildren();
            }
        }
        return value;
    }

    private List<String> readTexts(JsonParser p) throws IOException {
        if (p.currentToken() != JsonToken.START_ARRAY) {
            p.skipChildren();
            return List.of();
        }
        List<String> texts = new ArrayList<>();
        while (p.nextToken() != JsonToken.END_ARRAY) {
            String text = readText(p);
            if (text != null && !text.isEmpty()) {
                texts.add(text);
            }
        }
        return texts;
    }

    private List<Statement> readClaims(JsonParser p) throws IOException {
        if (p.currentToken() != JsonToken.START_OBJECT) {
            p.skipChildren();
            return List.of();
        }
        List<Statement> statements = new ArrayList<>();
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            p.nextToken();
            if (p.currentToken() != JsonToken.START_ARRAY) {
                p.skipChildren();
                continue;
            }
            while (p.nextToken() != JsonToken.END_ARRAY) {
                readStatement(p).ifPresent(statements::add);
            }
        }
        return statements;
    }

    private Optional<Statement> readStatement(JsonParser p) throws IOException {
        if (p.currentToken() != JsonToken.START_OBJECT) {
            p.skipChildren();
            return Optional.empty();
        }
        String id = null;
        String type = null;
        Snak snak = null;
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            String field = p.currentName();
            p.nextToken();
            switch (field) {
                case "id" -> id = p.getText();
                case "type" -> type = p.getText();
                case "mainsnak" -> snak = readSnak(p);
                default -> p.skipChildren();
            }
        }
        if (!"statement".equals(type) || id == null || snak == null) {
            return Optional.empty();
        }
        return Optional.of(new Statement(id, snak.property(), snak.value()));
    }

    private record Snak(String property, DataValue value) {}

    private Snak readSnak(JsonParser p) throws IOException {
        if (p.currentToken() != JsonToken.START_OBJECT) {
            p.skipChildren();
            return null;
        }
        String snaktype = null;
        String property = null;
        String datatype = null;
        Object raw = null;
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            String field = p.currentName();
            p.nextToken();
            switch (field) {
                case "snaktype" -> snaktype = p.getText();
                case "property" -> property = p.getText();
                case "datatype" -> datatype = p.getText();
                case "datavalue" -> raw = readDatavalue(p);
                default -> p.skipChildren();
            }
        }
        if (!"value".equals(snaktype) || property == null || datatype == null || raw == null) {
            return null;
        }
        DataValue value = toDataValue(datatype, raw);
        return value == null ? null : new Snak(property, value);
    }

    private Object readDatavalue(JsonParser p) throws IOException {
        if (p.currentToken() != JsonToken.START_OBJECT) {
            p.skipChildren();
            return null;
        }
        Object value = null;
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            String field = p.currentName();
            p.nextToken();
            if ("value".equals(field)) {
                value = readValue(p);
            } else {
                p.skipChildren();
            }
        }
        return value;
    }

    private Object readValue(JsonParser p) throws IOException {
        if (p.currentToken() != JsonToken.START_OBJECT) {
            return scalar(p);
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            String field = p.currentName();
            p.nextToken();
            Object value = scalar(p);
            if (value != null) {
                fields.put(field, value);
            }
        }
        return fields;
    }

    private Object scalar(JsonParser p) throws IOException {
        return switch (p.currentToken()) {
            case VALUE_STRING -> p.getText();
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> p.getNumberValue();
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            default -> {
                p.skipChildren();
                yield null;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private DataValue toDataValue(String datatype, Object raw) {
        if (datatype.equals("wikibase-item") || datatype.equals("wikibase-property")) {
            if (raw instanceof Map<?, ?> map && map.get("id") instanceof String id) {
                return new EntityRef(id);
            }
            return null;
        }
        if (raw instanceof Map<?, ?> map) {
            return new ClaimValue(datatype, (Map<String, Object>) map);
        }
        return new ClaimValue(datatype, Map.of("value", raw));
    }
}
