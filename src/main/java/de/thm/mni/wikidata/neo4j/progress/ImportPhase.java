package de.thm.mni.wikidata.neo4j.progress;

/**
 * The four top-level stages of the Neo4j batch importer, in execution order.
 * Only the first two read the dump; their progress is measured in bytes read.
 */
public enum ImportPhase {
    NODES("Node import", true),
    RELATIONSHIPS("Relationship import", true),
    LINKING("Relationship linking", false),
    POST_PROCESSING("Post processing", false);

    private final String description;
    private final boolean readsDump;

    ImportPhase(String description, boolean readsDump) {
        this.description = description;
        this.readsDump = readsDump;
    }

    public String description() {
        return description;
    }

    public boolean readsDump() {
        return readsDump;
    }

    public String label() {
        return (ordinal() + 1) + "/" + values().length + " " + description;
    }
}
