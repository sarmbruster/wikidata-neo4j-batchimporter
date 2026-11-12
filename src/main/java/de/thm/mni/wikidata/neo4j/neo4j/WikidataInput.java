package de.thm.mni.wikidata.neo4j.neo4j;

import de.thm.mni.wikidata.neo4j.dump.Compression;
import de.thm.mni.wikidata.neo4j.dump.DumpSource;
import de.thm.mni.wikidata.neo4j.mapping.NodeMapper;
import de.thm.mni.wikidata.neo4j.mapping.NodeRecord;
import de.thm.mni.wikidata.neo4j.mapping.PropertyIndex;
import de.thm.mni.wikidata.neo4j.mapping.RelationshipMapper;
import de.thm.mni.wikidata.neo4j.mapping.RelationshipRecord;
import de.thm.mni.wikidata.neo4j.parse.EntityParser;
import java.io.IOException;
import java.nio.file.Files;
import org.neo4j.batchimport.api.InputIterable;
import org.neo4j.batchimport.api.input.Collector;
import org.neo4j.batchimport.api.input.Group;
import org.neo4j.batchimport.api.input.IdType;
import org.neo4j.batchimport.api.input.Input;
import org.neo4j.batchimport.api.input.InputEntityVisitor;
import org.neo4j.batchimport.api.input.PropertySizeCalculator;
import org.neo4j.batchimport.api.input.ReadableGroups;
import org.neo4j.internal.batchimport.input.Groups;

public final class WikidataInput implements Input {

    private static final long BYTES_PER_ENTITY = 12_000;
    private static final double NODES_PER_ENTITY = 7.5;
    private static final double RELATIONSHIPS_PER_ENTITY = 16;
    private static final double PROPERTIES_PER_NODE = 3.5;
    private static final double PROPERTIES_PER_RELATIONSHIP = 1.5;
    private static final long BYTES_PER_PROPERTY = 24;

    private final DumpSource source;
    private final int chunkSize;
    private final Estimates estimates;
    private final EntityParser parser = new EntityParser();
    private final PropertyIndex propertyIndex = new PropertyIndex();
    private final NodeMapper nodeMapper = new NodeMapper(propertyIndex);
    private final RelationshipMapper relationshipMapper = new RelationshipMapper(propertyIndex);
    private final Groups groups = new Groups();
    private final Group group = groups.getOrCreate(null);

    public WikidataInput(DumpSource source, int chunkSize) throws IOException {
        this.source = source;
        this.chunkSize = chunkSize;
        this.estimates = estimate(source);
    }

    @Override
    public InputIterable nodes(Collector collector) {
        return new DumpInputIterable<NodeRecord>(source, chunkSize, parser, nodeMapper::map, this::visitNode);
    }

    @Override
    public InputIterable relationships(Collector collector) {
        return new DumpInputIterable<RelationshipRecord>(
                source, chunkSize, parser, relationshipMapper::map, this::visitRelationship);
    }

    @Override
    public IdType idType() {
        return IdType.STRING;
    }

    @Override
    public ReadableGroups groups() {
        return groups;
    }

    @Override
    public Estimates validateAndEstimate(PropertySizeCalculator calculator) {
        return estimates;
    }

    static Estimates estimate(DumpSource source) throws IOException {
        long uncompressedBytes = (long) (Files.size(source.file()) * expansionFactor(source.compression()));
        double entities = Math.max(1, uncompressedBytes / (double) BYTES_PER_ENTITY);
        long nodes = Math.max(1, (long) (entities * NODES_PER_ENTITY));
        long relationships = Math.max(1, (long) (entities * RELATIONSHIPS_PER_ENTITY));
        long nodeProperties = (long) (nodes * PROPERTIES_PER_NODE);
        long relationshipProperties = (long) (relationships * PROPERTIES_PER_RELATIONSHIP);
        return Input.knownEstimates(
                nodes,
                relationships,
                nodeProperties,
                relationshipProperties,
                nodeProperties * BYTES_PER_PROPERTY,
                relationshipProperties * BYTES_PER_PROPERTY,
                nodes * 2);
    }

    private static double expansionFactor(Compression compression) {
        return switch (compression) {
            case GZIP -> 10;
            case BZIP2 -> 14;
            case NONE, AUTO -> 1;
        };
    }

    private void visitNode(NodeRecord node, InputEntityVisitor visitor) {
        visitor.id(node.id(), group);
        visitor.labels(node.labels().toArray(String[]::new));
        node.properties().forEach(visitor::property);
    }

    private void visitRelationship(RelationshipRecord relationship, InputEntityVisitor visitor) {
        visitor.startId(relationship.startId(), group);
        visitor.endId(relationship.endId(), group);
        visitor.type(relationship.type());
        relationship.properties().forEach(visitor::property);
    }
}
