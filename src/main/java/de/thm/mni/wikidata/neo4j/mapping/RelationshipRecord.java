package de.thm.mni.wikidata.neo4j.mapping;

import java.util.Map;

public record RelationshipRecord(String startId, String endId, String type, Map<String, Object> properties) {}
