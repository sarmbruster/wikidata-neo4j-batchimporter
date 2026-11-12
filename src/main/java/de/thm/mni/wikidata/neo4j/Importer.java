package de.thm.mni.wikidata.neo4j;

import java.io.IOException;

@FunctionalInterface
public interface Importer {

    ImportResult run(ImportRequest request) throws IOException;
}
