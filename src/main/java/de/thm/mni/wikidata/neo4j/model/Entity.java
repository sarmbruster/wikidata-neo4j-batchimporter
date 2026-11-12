package de.thm.mni.wikidata.neo4j.model;

import java.util.List;

public record Entity(
        String id,
        EntityType type,
        String label,
        List<String> descriptions,
        List<String> aliases,
        String datatype,
        List<Statement> statements) {}
