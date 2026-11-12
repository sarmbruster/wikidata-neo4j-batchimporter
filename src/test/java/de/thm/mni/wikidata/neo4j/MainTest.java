package de.thm.mni.wikidata.neo4j;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void mainRunsWithoutArguments() {
        assertThatCode(() -> Main.main(new String[0])).doesNotThrowAnyException();
    }
}
