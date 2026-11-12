# Projekt
Siehe @SPEC.md für Anforderungen. Aufruf, Optionen und Graphmodell stehen in @README.md, Entscheidungen in `docs/adr/`.

## Befehle
- Build+Tests (inkl. Integrationstests `*IT`, die das gebaute `target/tools.jar` brauchen): `./mvnw -q verify`
- Einzeltest: `./mvnw -q test -Dtest=NodeMapperTest`
- `*IT`-Tests laufen nur mit `verify`, nicht mit `test`

## Architektur
- Der Neo4j-BatchImporter (interne API, Version in `pom.xml` fest gepinnt, 5.26.x) verarbeitet erst alle Knoten, dann alle Kanten. Deshalb liest das Tool den Dump zweimal (ADR 0003): Pass 1 liefert Knoten und füllt den `PropertyIndex` (Property-Id → Kantentyp), Pass 2 liefert Kanten mit dem vollständigen Index.
- Keine Zwischenpuffer oder Spool-Dateien einbauen: Ein Puffer für Kanten blockiert den Leser während der Knotenphase. Der Importer zieht Chunks aus mehreren Threads (`LineChunkReader` liest unter Lock, Parsing und Mapping laufen danach parallel).
- Datenfluss: `dump` (Zeilen) → `parse` (Entity) → `mapping` (`NodeMapper`, `RelationshipMapper`) → `neo4j` (`WikidataInput`, `ImporterRunner`). `WikidataImport` verbindet alles, `cli` ist nur picocli.
- `<pfad>` ist das Neo4j-Home, nicht das Datenbankverzeichnis (ADR 0005): Die Datenbank liegt unter `<pfad>/data/databases/neo4j`.
- Kanten auf Entities, die im Dump fehlen, landen im Error-Log und brechen den Import nicht ab (ADR 0004).

## Tests
- `Fixtures` und `Neo4jHome` (Testklassen) laden Fixtures bzw. öffnen die erzeugte Datenbank und zählen mit Cypher.
- `wikidata-100.json` ist 20 MB groß (nur Items): gz und bz2 werden zur Laufzeit im Test erzeugt, nicht eingecheckt.
- `mixed.json` ist das kleine Fixture (Properties nach den Items, Vorwärtsverweis, Verweis auf fehlende `Q404404`) für Mapping- und Importtests.

## Regeln
- Nach jeder Änderung `./mvnw -q verify` ausführen, erst bei grün fertig melden
- Keine neuen Dependencies ohne Rückfrage
- Records statt Lombok, keine Field-Injection
- Tests mit AssertJ, Fixtures aus src/test/resources
