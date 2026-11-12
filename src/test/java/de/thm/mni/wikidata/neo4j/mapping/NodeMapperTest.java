package de.thm.mni.wikidata.neo4j.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import de.thm.mni.wikidata.neo4j.Fixtures;
import de.thm.mni.wikidata.neo4j.model.Entity;
import de.thm.mni.wikidata.neo4j.model.EntityType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NodeMapperTest {

    private Map<String, Entity> entities;
    private PropertyIndex index;
    private NodeMapper mapper;

    @BeforeEach
    void setUp() {
        entities = Fixtures.entities(Fixtures.MIXED);
        index = new PropertyIndex();
        mapper = new NodeMapper(index);
    }

    @Test
    void mapsItemToEntityNodeWithEnglishTexts() {
        NodeRecord node = mapper.map(entities.get("Q100")).getFirst();

        assertThat(node.id()).isEqualTo("Q100");
        assertThat(node.labels()).containsExactly("Entity", "Item");
        assertThat(node.properties())
                .containsEntry("id", "Q100")
                .containsEntry("label", "Berlin")
                .containsEntry("slug", "berlin")
                .containsEntry("descriptions", new String[] {"capital of Germany"})
                .containsEntry("aliases", new String[] {"Berlin, Germany"})
                .doesNotContainKey("datatype");
    }

    @Test
    void mapsPropertyToEntityNodeWithDatatype() {
        NodeRecord node = mapper.map(entities.get("P2046")).getFirst();

        assertThat(node.labels()).containsExactly("Entity", "Property");
        assertThat(node.properties()).containsEntry("datatype", "quantity").containsEntry("label", "area");
    }

    @Test
    void createsClaimNodesOnlyForNonEntityValuesKeyedByOwnerAndStatementPosition() {
        List<NodeRecord> nodes = mapper.map(entities.get("Q100"));

        assertThat(nodes).extracting(NodeRecord::id).containsExactly("Q100", "Q100#3", "Q100#4");
    }

    @Test
    void dropsUnitAndGlobeThatReferenceEntities() {
        List<NodeRecord> nodes = mapper.map(entities.get("Q100"));

        NodeRecord quantity = nodes.get(1);
        assertThat(quantity.labels()).containsExactly("Claim", "Quantity");
        assertThat(quantity.properties()).containsEntry("id", "Q100$4").containsEntry("amount", "+891.8").doesNotContainKey("unit");

        NodeRecord coordinate = nodes.get(2);
        assertThat(coordinate.labels()).containsExactly("Claim", "GlobeCoordinate");
        assertThat(coordinate.properties())
                .containsEntry("latitude", 52.52)
                .containsEntry("longitude", 13.405)
                .doesNotContainKeys("globe", "altitude");
    }

    @Test
    void keepsUnitThatIsNotAnEntityReference() {
        NodeRecord population = mapper.map(entities.get("Q300")).get(1);

        assertThat(population.properties()).containsEntry("amount", "+84000000").containsEntry("unit", "1");
    }

    @Test
    void wrapsPlainValues() {
        List<NodeRecord> nodes = mapper.map(entities.get("Q300"));

        NodeRecord url = nodes.getLast();
        assertThat(url.labels()).containsExactly("Claim", "Url");
        assertThat(url.properties()).containsEntry("value", "https://www.germany.example");
    }

    @Test
    void registersPropertyLabelsAsRelationshipTypes() {
        mapper.map(entities.get("P31"));
        mapper.map(entities.get("P2046"));
        mapper.map(entities.get("Q100"));

        assertThat(index.typeOf("P31")).isEqualTo("INSTANCE_OF");
        assertThat(index.typeOf("P2046")).isEqualTo("AREA");
    }

    @Test
    void fallsBackToClaimForUnknownOrUnlabeledProperties() {
        mapper.map(new Entity("P1", EntityType.PROPERTY, "", List.of(), List.of(), "string", List.of()));

        assertThat(index.typeOf("P1")).isEqualTo("CLAIM");
        assertThat(index.typeOf("P404")).isEqualTo("CLAIM");
    }
}
