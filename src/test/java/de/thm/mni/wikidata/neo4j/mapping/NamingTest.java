package de.thm.mni.wikidata.neo4j.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NamingTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "instance of|INSTANCE_OF",
                "country|COUNTRY",
                "2D map|_2D_MAP",
                "date of birth (approximate)|DATE_OF_BIRTH_APPROXIMATE",
                "Übergang|UBERGANG",
                "  spaced   out |SPACED_OUT",
                "country/region|COUNTRYREGION",
                "a - b|A_B",
                "!!!|''",
                "''|''"
            })
    void turnsPropertyLabelsIntoRelationshipTypes(String label, String expected) {
        assertThat(Naming.relationType(label)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "globe-coordinate|GlobeCoordinate",
                "external-id|ExternalId",
                "commonsMedia|Commonsmedia",
                "string|String",
                "monolingualtext|Monolingualtext",
                "wikibase-lexeme|WikibaseLexeme",
                "geo-shape|GeoShape",
                "tabular-data|TabularData"
            })
    void turnsDatatypesIntoClaimLabels(String datatype, String expected) {
        assertThat(Naming.claimLabel(datatype)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Belgium|belgium",
                "Kingdom of Belgium|kingdom-of-belgium",
                "São Paulo|sao-paulo",
                "''|''"
            })
    void turnsLabelsIntoSlugs(String label, String expected) {
        assertThat(Naming.slug(label)).isEqualTo(expected);
    }
}
