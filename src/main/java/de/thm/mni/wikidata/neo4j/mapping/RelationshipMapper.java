package de.thm.mni.wikidata.neo4j.mapping;

import de.thm.mni.wikidata.neo4j.model.DataValue.ClaimValue;
import de.thm.mni.wikidata.neo4j.model.DataValue.EntityRef;
import de.thm.mni.wikidata.neo4j.model.Entity;
import de.thm.mni.wikidata.neo4j.model.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RelationshipMapper {

    private final PropertyIndex propertyIndex;

    public RelationshipMapper(PropertyIndex propertyIndex) {
        this.propertyIndex = propertyIndex;
    }

    public List<RelationshipRecord> map(Entity entity) {
        List<RelationshipRecord> relationships = new ArrayList<>();
        List<Statement> statements = entity.statements();
        for (int index = 0; index < statements.size(); index++) {
            Statement statement = statements.get(index);
            String type = propertyIndex.typeOf(statement.propertyId());
            switch (statement.value()) {
                case EntityRef ref -> relationships.add(new RelationshipRecord(
                        entity.id(),
                        ref.id(),
                        type,
                        Map.of("by", statement.propertyId(), "id", statement.id())));
                case ClaimValue claim -> {
                    String claimKey = NodeMapper.claimKey(entity.id(), index);
                    relationships.add(
                            new RelationshipRecord(entity.id(), claimKey, type, Map.of("by", statement.propertyId())));
                    addTypeRelationship(relationships, claimKey, claim);
                }
            }
        }
        return relationships;
    }

    private void addTypeRelationship(List<RelationshipRecord> relationships, String claimId, ClaimValue claim) {
        String field;
        String type;
        switch (claim.datatype()) {
            case "quantity" -> {
                field = "unit";
                type = "UNIT_TYPE";
            }
            case "globe-coordinate" -> {
                field = "globe";
                type = "GLOBE_TYPE";
            }
            default -> {
                return;
            }
        }
        Object url = claim.fields().get(field);
        if (NodeMapper.isEntityReference(field, url)) {
            String entityUrl = (String) url;
            String entityId = entityUrl.substring(entityUrl.lastIndexOf('/') + 1).strip();
            relationships.add(new RelationshipRecord(claimId, entityId, type, Map.of()));
        }
    }
}
