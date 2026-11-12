package de.thm.mni.wikidata.neo4j.mapping;

import de.thm.mni.wikidata.neo4j.model.DataValue.ClaimValue;
import de.thm.mni.wikidata.neo4j.model.Entity;
import de.thm.mni.wikidata.neo4j.model.EntityType;
import de.thm.mni.wikidata.neo4j.model.Statement;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NodeMapper {

    static final String ENTITY_LABEL = "Entity";
    static final String CLAIM_LABEL = "Claim";
    static final String ENTITY_URL_PREFIX = "http";

    private final PropertyIndex propertyIndex;

    public NodeMapper(PropertyIndex propertyIndex) {
        this.propertyIndex = propertyIndex;
    }

    public List<NodeRecord> map(Entity entity) {
        propertyIndex.register(entity);
        List<NodeRecord> nodes = new ArrayList<>();
        nodes.add(entityNode(entity));
        List<Statement> statements = entity.statements();
        for (int index = 0; index < statements.size(); index++) {
            Statement statement = statements.get(index);
            if (statement.value() instanceof ClaimValue claim) {
                nodes.add(claimNode(claimKey(entity.id(), index), statement.id(), claim));
            }
        }
        return nodes;
    }

    private NodeRecord entityNode(Entity entity) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", entity.id());
        properties.put("label", entity.label());
        properties.put("slug", Naming.slug(entity.label()));
        properties.put("descriptions", entity.descriptions().toArray(String[]::new));
        properties.put("aliases", entity.aliases().toArray(String[]::new));
        if (entity.datatype() != null) {
            properties.put("datatype", entity.datatype());
        }
        String typeLabel = entity.type() == EntityType.ITEM ? "Item" : "Property";
        return new NodeRecord(entity.id(), List.of(ENTITY_LABEL, typeLabel), properties);
    }

    static String claimKey(String entityId, int statementIndex) {
        return entityId + "#" + statementIndex;
    }

    private NodeRecord claimNode(String nodeKey, String statementId, ClaimValue claim) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", statementId);
        claim.fields().forEach((key, value) -> {
            if (!isEntityReference(key, value)) {
                properties.put(key, storable(value));
            }
        });
        return new NodeRecord(nodeKey, List.of(CLAIM_LABEL, Naming.claimLabel(claim.datatype())), properties);
    }

    static boolean isEntityReference(String key, Object value) {
        return (key.equals("unit") || key.equals("globe"))
                && value instanceof String url
                && url.startsWith(ENTITY_URL_PREFIX);
    }

    private static Object storable(Object value) {
        return value instanceof BigInteger || value instanceof BigDecimal ? value.toString() : value;
    }
}
