package de.thm.mni.wikidata.neo4j.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import de.thm.mni.wikidata.neo4j.Fixtures;
import de.thm.mni.wikidata.neo4j.model.Entity;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RelationshipMapperTest {

    private Map<String, Entity> entities;
    private RelationshipMapper mapper;

    @BeforeEach
    void setUp() {
        entities = Fixtures.entities(Fixtures.MIXED);
        var index = new PropertyIndex();
        entities.values().forEach(index::register);
        mapper = new RelationshipMapper(index);
    }

    @Test
    void linksEntitiesViaPropertyLabelTypeWithPropertyAndStatementId() {
        var relationships = mapper.map(entities.get("Q100"));

        assertThat(relationships.getFirst())
                .isEqualTo(new RelationshipRecord(
                        "Q100", "Q200", "INSTANCE_OF", Map.of("by", "P31", "id", "Q100$1")));
        assertThat(relationships.get(1))
                .isEqualTo(new RelationshipRecord("Q100", "Q300", "COUNTRY", Map.of("by", "P17", "id", "Q100$2")));
    }

    @Test
    void keepsReferencesToEntitiesThatAreNotInTheDump() {
        assertThat(mapper.map(entities.get("Q100")))
                .contains(new RelationshipRecord(
                        "Q100", "Q404404", "NAMED_AFTER", Map.of("by", "P138", "id", "Q100$3")));
    }

    @Test
    void linksEntityToClaimNodeKeyedByOwnerAndStatementPosition() {
        assertThat(mapper.map(entities.get("Q100")))
                .contains(
                        new RelationshipRecord("Q100", "Q100#3", "AREA", Map.of("by", "P2046")),
                        new RelationshipRecord("Q100", "Q100#4", "COORDINATE_LOCATION", Map.of("by", "P625")));
    }

    @Test
    void linksQuantityAndGlobeCoordinateToTheirEntities() {
        assertThat(mapper.map(entities.get("Q100")))
                .contains(
                        new RelationshipRecord("Q100#3", "Q712226", "UNIT_TYPE", Map.of()),
                        new RelationshipRecord("Q100#4", "Q2", "GLOBE_TYPE", Map.of()));
    }

    @Test
    void doesNotLinkUnitsThatAreNoEntityReferences() {
        assertThat(mapper.map(entities.get("Q300")))
                .extracting(RelationshipRecord::type)
                .containsExactly("CAPITAL", "POPULATION", "OFFICIAL_NAME", "OFFICIAL_WEBSITE");
    }

    @Test
    void usesClaimTypeForPropertiesWithoutKnownLabel() {
        var mapperWithoutIndex = new RelationshipMapper(new PropertyIndex());

        assertThat(mapperWithoutIndex.map(entities.get("Q300")).getFirst().type()).isEqualTo("CLAIM");
    }

    @Test
    void createsNoRelationshipsForEntitiesWithoutStatements() {
        assertThat(mapper.map(entities.get("Q200"))).isEmpty();
    }
}
