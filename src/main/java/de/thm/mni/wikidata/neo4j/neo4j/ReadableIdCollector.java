package de.thm.mni.wikidata.neo4j.neo4j;

import java.util.Map;
import org.neo4j.batchimport.api.input.Collector;
import org.neo4j.batchimport.api.input.Group;
import org.neo4j.common.EntityType;

/** Translates encoded integer input ids back to Wikidata keys before they reach the error log. */
final class ReadableIdCollector implements Collector {

    private final Collector delegate;

    ReadableIdCollector(Collector delegate) {
        this.delegate = delegate;
    }

    @Override
    public void collectBadRelationship(
            Object startId, Group startIdGroup, Object type, Object endId, Group endIdGroup, Object specificValue) {
        delegate.collectBadRelationship(
                readable(startId), startIdGroup, type, readable(endId), endIdGroup, readable(specificValue));
    }

    @Override
    public void collectDuplicateNode(Object id, long actualId, Group group) {
        delegate.collectDuplicateNode(readable(id), actualId, group);
    }

    @Override
    public void collectEntityViolatingConstraint(
            Object id, long actualId, Map<String, Object> properties, String constraintDescription, EntityType entityType) {
        delegate.collectEntityViolatingConstraint(readable(id), actualId, properties, constraintDescription, entityType);
    }

    @Override
    public void collectRelationshipViolatingConstraint(
            Map<String, Object> properties,
            String constraintDescription,
            Object startId,
            Group startIdGroup,
            String type,
            Object endId,
            Group endIdGroup) {
        delegate.collectRelationshipViolatingConstraint(
                properties, constraintDescription, readable(startId), startIdGroup, type, readable(endId), endIdGroup);
    }

    @Override
    public void collectExtraColumns(String source, long row, String value) {
        delegate.collectExtraColumns(source, row, value);
    }

    @Override
    public void collectSchemaCommandFailure(EntityType entityType, String message) {
        delegate.collectSchemaCommandFailure(entityType, message);
    }

    @Override
    public long badEntries() {
        return delegate.badEntries();
    }

    @Override
    public boolean isCollectingBadRelationships() {
        return delegate.isCollectingBadRelationships();
    }

    @Override
    public void close() {
        delegate.close();
    }

    private static Object readable(Object id) {
        return id instanceof Long encoded ? InputIds.describe(encoded) : id;
    }
}
