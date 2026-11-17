package de.thm.mni.wikidata.neo4j;

import de.thm.mni.wikidata.neo4j.dump.Compression;
import java.nio.file.Path;

/** @param maxOffHeapMemory memory budget of the importer caches outside the JVM heap in bytes */
public record ImportRequest(
        Path jsonFile,
        Compression compression,
        Path neo4jHome,
        Path errorLog,
        int chunkSize,
        boolean progress,
        Path logFile,
        long maxOffHeapMemory) {}
