package de.thm.mni.wikidata.neo4j;

import de.thm.mni.wikidata.neo4j.cli.WikidataImportCommand;
import picocli.CommandLine;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new WikidataImportCommand()).execute(args));
    }
}
