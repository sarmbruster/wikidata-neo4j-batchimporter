package de.thm.mni.wikidata.neo4j.dump;

import java.nio.file.Path;
import java.util.Locale;

public enum Compression {
    AUTO,
    NONE,
    GZIP,
    BZIP2;

    public Compression resolve(Path file) {
        if (this != AUTO) {
            return this;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".gz")) {
            return GZIP;
        }
        if (name.endsWith(".bz2")) {
            return BZIP2;
        }
        return NONE;
    }
}
