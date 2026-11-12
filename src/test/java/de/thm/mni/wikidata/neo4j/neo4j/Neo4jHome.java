package de.thm.mni.wikidata.neo4j.neo4j;

import java.nio.file.Path;
import java.util.function.Function;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.Transaction;

public final class Neo4jHome {

    private Neo4jHome() {
    }

    public static <T> T read(Path home, Function<Transaction, T> query) {
        var dbms = new DatabaseManagementServiceBuilder(home).build();
        try (var tx = dbms.database(ImporterRunner.DATABASE).beginTx()) {
            return query.apply(tx);
        } finally {
            dbms.shutdown();
        }
    }

    public static long count(Path home, String cypher) {
        return read(home, tx -> tx.execute(cypher).<Long>columnAs("c").next());
    }
}
