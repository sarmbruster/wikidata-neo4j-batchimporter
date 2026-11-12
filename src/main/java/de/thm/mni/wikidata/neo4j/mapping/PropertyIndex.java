package de.thm.mni.wikidata.neo4j.mapping;

import de.thm.mni.wikidata.neo4j.model.Entity;
import de.thm.mni.wikidata.neo4j.model.EntityType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PropertyIndex {

    static final String FALLBACK_TYPE = "CLAIM";

    private final Map<String, String> typesByPropertyId = new ConcurrentHashMap<>();

    public void register(Entity entity) {
        if (entity.type() == EntityType.PROPERTY) {
            typesByPropertyId.put(entity.id(), Naming.relationType(entity.label()));
        }
    }

    public String typeOf(String propertyId) {
        String type = typesByPropertyId.get(propertyId);
        return type == null || type.isEmpty() ? FALLBACK_TYPE : type;
    }
}
