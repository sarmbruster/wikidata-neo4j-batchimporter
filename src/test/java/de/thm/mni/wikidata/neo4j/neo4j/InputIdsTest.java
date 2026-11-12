package de.thm.mni.wikidata.neo4j.neo4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InputIdsTest {

    @ParameterizedTest
    @ValueSource(strings = {"Q1", "P1", "Q1#0", "P1#0", "Q42", "Q138000000", "Q138000000#5000", "P31#1048575"})
    void encodesAndDescribesKeysSymmetrically(String key) {
        assertThat(InputIds.describe(InputIds.encode(key))).isEqualTo(key);
    }

    @Test
    void keepsItemsPropertiesAndClaimsApart() {
        List<Long> ids = List.of("Q1", "P1", "Q1#0", "P1#0", "Q1#1", "Q2").stream()
                .map(InputIds::encode)
                .toList();

        assertThat(ids).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Q18014398509481983", "P18014398509481983", "Q8589934591#1048575", "P8589934591#1048575"})
    void staysWithinTheIntegerRangeNeo4jAccepts(String key) {
        assertThat(InputIds.encode(key)).isBetween(0L, InputIds.MAX_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Q", "L1", "Qabc", "Q-1", "Q1#", "Q1#x", "Q8589934592#0", "Q1#1048576", "Q18014398509481984"})
    void rejectsKeysThatCannotBeEncoded(String key) {
        assertThatThrownBy(() -> InputIds.encode(key)).isInstanceOf(IllegalArgumentException.class);
    }
}
