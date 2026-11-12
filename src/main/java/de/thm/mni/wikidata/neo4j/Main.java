package de.thm.mni.wikidata.neo4j;

import de.thm.mni.wikidata.neo4j.cli.WikidataImportCommand;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(WikidataImportCommand.commandLine(new WikidataImport(System.out, System.err, System.console() != null)).execute(args));
    }
}
