package de.thm.mni.wikidata.neo4j.model;

import java.util.Optional;

public enum EntityType {
    ITEM,
    PROPERTY;

    public static Optional<EntityType> fromJson(String type) {
        return switch (type == null ? "" : type) {
            case "item" -> Optional.of(ITEM);
            case "property" -> Optional.of(PROPERTY);
            default -> Optional.empty();
        };
    }
}
