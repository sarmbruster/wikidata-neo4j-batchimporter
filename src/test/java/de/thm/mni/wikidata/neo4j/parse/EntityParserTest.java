package de.thm.mni.wikidata.neo4j.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.thm.mni.wikidata.neo4j.Fixtures;
import de.thm.mni.wikidata.neo4j.model.DataValue.ClaimValue;
import de.thm.mni.wikidata.neo4j.model.DataValue.EntityRef;
import de.thm.mni.wikidata.neo4j.model.Entity;
import de.thm.mni.wikidata.neo4j.model.EntityType;
import de.thm.mni.wikidata.neo4j.model.Statement;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EntityParserTest {

    private final EntityParser parser = new EntityParser();

    @Test
    void parsesBelgiumFromTheDumpFixture() {
        Entity belgium = parse(records("/wikidata-100.json", 1).getFirst());

        assertThat(belgium.id()).isEqualTo("Q31");
        assertThat(belgium.type()).isEqualTo(EntityType.ITEM);
        assertThat(belgium.label()).isEqualTo("Belgium");
        assertThat(belgium.descriptions()).containsExactly("country in western Europe");
        assertThat(belgium.aliases()).containsExactly("Kingdom of Belgium");
        assertThat(belgium.datatype()).isNull();
    }

    @Test
    void countsStatementsPerDatatypeForBelgium() {
        Entity belgium = parse(records("/wikidata-100.json", 1).getFirst());

        Map<String, Long> perKind = belgium.statements().stream()
                .collect(Collectors.groupingBy(
                        statement -> switch (statement.value()) {
                            case EntityRef ref -> "entity";
                            case ClaimValue claim -> claim.datatype();
                        },
                        Collectors.counting()));

        assertThat(belgium.statements()).hasSize(1048);
        assertThat(perKind)
                .containsEntry("entity", 256L)
                .containsEntry("quantity", 401L)
                .containsEntry("external-id", 266L)
                .containsEntry("globe-coordinate", 6L);
        assertThat(belgium.statements().stream().map(Statement::id)).doesNotHaveDuplicates();
        assertThat(belgium.statements())
                .filteredOn(statement -> statement.propertyId().equals("P31"))
                .first()
                .extracting(Statement::value)
                .isEqualTo(new EntityRef("Q3624078"));
    }

    @Test
    void parsesAllRecordsOfTheDumpFixture() {
        List<Entity> entities = records("/wikidata-100.json", Integer.MAX_VALUE).stream()
                .map(this::parse)
                .toList();

        assertThat(entities).hasSize(99).allSatisfy(entity -> {
            assertThat(entity.type()).isEqualTo(EntityType.ITEM);
            assertThat(entity.id()).startsWith("Q");
        });
    }

    @Test
    void parsesPropertiesAndTreatsEmptyArraysAsMissing() {
        Map<String, Entity> byId = mixed();

        Entity instanceOf = byId.get("P31");
        assertThat(instanceOf.type()).isEqualTo(EntityType.PROPERTY);
        assertThat(instanceOf.datatype()).isEqualTo("wikibase-item");
        assertThat(instanceOf.label()).isEqualTo("instance of");
        assertThat(instanceOf.descriptions()).isEmpty();
        assertThat(instanceOf.aliases()).isEmpty();
        assertThat(instanceOf.statements()).isEmpty();
    }

    @Test
    void readsOnlyEnglishTexts() {
        Entity germany = mixed().get("Q300");

        assertThat(germany.label()).isEqualTo("Germany");
        assertThat(germany.aliases()).containsExactly("Federal Republic of Germany", "Deutschland");
    }

    @Test
    void mapsEntityReferencesAndClaimValues() {
        Entity berlin = mixed().get("Q100");

        assertThat(berlin.statements()).extracting(Statement::id).containsExactly("Q100$1", "Q100$2", "Q100$3", "Q100$4", "Q100$5");
        assertThat(berlin.statements().get(0).value()).isEqualTo(new EntityRef("Q200"));
        assertThat(berlin.statements().get(3).value())
                .isEqualTo(new ClaimValue(
                        "quantity", Map.of("amount", "+891.8", "unit", "http://www.wikidata.org/entity/Q712226")));
        assertThat(((ClaimValue) berlin.statements().get(4).value()).fields())
                .containsOnlyKeys("latitude", "longitude", "precision", "globe")
                .containsEntry("latitude", 52.52)
                .containsEntry("globe", "http://www.wikidata.org/entity/Q2");
    }

    @Test
    void wrapsPlainValuesAndSkipsSomevalueSnaks() {
        Entity germany = mixed().get("Q300");

        assertThat(germany.statements()).extracting(Statement::id).containsExactly("Q300$1", "Q300$2", "Q300$4", "Q300$5");
        assertThat(germany.statements().get(3).value())
                .isEqualTo(new ClaimValue("url", Map.of("value", "https://www.germany.example")));
        assertThat(germany.statements().get(2).value())
                .isEqualTo(new ClaimValue("monolingualtext", Map.of("text", "Deutschland", "language", "de")));
    }

    @Test
    void ignoresUnsupportedEntityTypes() {
        assertThat(parser.parse("{\"type\":\"lexeme\",\"id\":\"L1\"}")).isEmpty();
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> parser.parse("{\"type\":\"item\",\"id\":"))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Invalid entity JSON");
        assertThatThrownBy(() -> parser.parse("[1,2]")).isInstanceOf(UncheckedIOException.class);
    }

    private Entity parse(String json) {
        return parser.parse(json).orElseThrow();
    }

    private Map<String, Entity> mixed() {
        return Fixtures.entities(Fixtures.MIXED);
    }

    private List<String> records(String resource, int max) {
        return Fixtures.records(resource, max);
    }
}
