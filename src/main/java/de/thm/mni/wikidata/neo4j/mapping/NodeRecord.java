package de.thm.mni.wikidata.neo4j.mapping;

import java.util.List;
import java.util.Map;

public record NodeRecord(String id, List<String> labels, Map<String, Object> properties) {}
