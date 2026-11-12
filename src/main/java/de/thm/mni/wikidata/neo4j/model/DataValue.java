package de.thm.mni.wikidata.neo4j.model;

import java.util.Map;

public sealed interface DataValue {

    record EntityRef(String id) implements DataValue {}

    record ClaimValue(String datatype, Map<String, Object> fields) implements DataValue {}
}
