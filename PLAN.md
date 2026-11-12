# Plan: Paketstruktur & Implementierungsschritte (wikidata-neo4j-batchimporter)

## Context
Das Repo ist bisher leer bis auf `SPEC.md`, `CLAUDE.md` und das Fixture `src/test/resources/wikidata-100.json` (99 Items, keine Properties, eine Zeile pro Record, umschlossen von `[` / `]`).
Ziel laut SPEC: CLI `java -jar tools.jar <json-file> <pfad>` (aufgerufen über ein Shellscript) erzeugt mit dem Neo4j-Batchimporter auf Java-Ebene eine valide, nicht leere Neo4j-Datenbank.

Getroffene Entscheidungen:
- Neo4j 5.x Community (5.26 LTS)
- Graphmodell wie bei findie/wikidata-neo4j-importer
- Base-Package `de.thm.mni.wikidata.neo4j`
- Dependencies Jackson und commons-compress (bz2), Fat-JAR über maven-shade-plugin
- Nicht auflösbare Relationships kommen ins Error-Log.
- **Die Datei wird zweimal gelesen:** im ersten Durchlauf nur Knoten, im zweiten nur Kanten. Es gibt keine großen Zwischenpuffer.

## Architektur: zwei Durchläufe, direkt vom Importer gezogen

Der Neo4j-Importer arbeitet sequenziell: `importNodes()` → `prepareIdMapper()` → `importRelationships()`. Das ist im Quellcode `ParallelBatchImporter.doImport` (5.25/dev) geprüft. Daraus ergibt sich das Design:

- **`Input.nodes()`** öffnet den Dump und liefert nur Knoten:
  - `:Entity:Item` und `:Entity:Property`
  - `:Claim:<Datatype>`-Knoten für Claims ohne Entity-Ziel
- **`Input.relationships()`** öffnet den Dump ein zweites Mal und liefert nur Kanten:
  - Entity → Entity
  - Entity → Claim
  - `UNIT_TYPE` und `GLOBE_TYPE`
- **Kein Zwischenpuffer:** Der Importer zieht über `InputIterator.next(InputChunk)` aus mehreren Threads.
  - Unter einem Lock wird nur ein Chunk roher Zeilen gelesen, z. B. 1.000 Zeilen. Das JSON-Parsing und das Mapping laufen danach ohne Lock im jeweiligen Importer-Thread.
  - Im Speicher liegen damit höchstens „Anzahl Threads × Chunk“ Zeilen.
  - Backpressure entsteht von selbst: Es wird nur gelesen, wenn der Importer einen neuen Chunk anfordert. Es gibt keinen Ringpuffer und keinen Spool.
- **PropertyIndex:** eine Zuordnung Property-Id → Relationship-Typ.
  - Er wird im ersten Durchlauf als Nebeneffekt gefüllt. Das sind etwa 12.000 Einträge in einer `ConcurrentHashMap`.
  - Im zweiten Durchlauf ist er vollständig, auch wenn Properties erst spät im Dump stehen.
- **Claim-Knoten und Claim-Kanten:** Beide Durchläufe erzeugen dieselben Claim-Ids, nämlich die Wikidata-Statement-Ids. Die Kanten finden ihre Claim-Knoten deshalb über die String-Ids des Importers.
- **Nicht auflösbare Kanten:** `Collectors.badCollector(OutputStream, UNLIMITED_TOLERANCE)` schreibt sie in `--error-log` (Standard `<pfad>.errors.log`). Der Import bricht nicht ab. Kanten auf Entities, die weiter unten in der Datei stehen, werden normal aufgelöst, weil alle Knoten vor der ersten Kante feststehen.
- **Kosten:** Der Dump wird zweimal gelesen und dekomprimiert. Bei bz2 ist die Dekompression der Engpass. Das ist der Preis dafür, dass es keine Zwischendatei gibt.

## Paketstruktur (`de.thm.mni.wikidata.neo4j`)
```
de.thm.mni.wikidata.neo4j
├── cli        WikidataImportCommand (picocli), Main – Argumente, Exit-Codes
├── dump       DumpSource (öffnet Datei, Compression AUTO|NONE|GZIP|BZIP2),
│              LineChunkReader – liest unter Lock n Rohzeilen, strippt [ ] und Kommas
├── model      Records: Entity, EntityType, Statement, DataValue (sealed: EntityRef, Quantity,
│              GlobeCoordinate, Time, Monolingualtext, StringValue, ...)
├── parse      EntityParser – JSON-Zeile → Entity (Jackson, nur en, nur mainsnak/value)
├── mapping    Naming (slugify/relationify/labelify), PropertyIndex (thread-safe),
│              NodeMapper – Entity → Knoten; RelationshipMapper – Entity + PropertyIndex → Kanten
├── neo4j      WikidataInput (implements Input), NodeInputIterator / RelationshipInputIterator
│              (je über LineChunkReader), ImporterRunner (BatchImporterFactory, Config, Collector)
└── WikidataImport   Orchestrator
```
Weitere Dateien: `bin/wikidata-import.sh` und ADRs in `docs/adr/`:
- 0001: Neo4j 5 mit internem BatchImporter
- 0002: Graphmodell nach findie
- 0003: Zwei Durchläufe statt Zwischenpuffer
- 0004: Nicht auflösbare Relationships ins Error-Log

## Implementierungsschritte (jeder einzeln testbar)

1. **Projektgerüst**
   - Inhalt: Maven-Wrapper, `pom.xml` (Java 21, picocli, neo4j 5.26, jackson-databind, commons-compress, JUnit, AssertJ, shade), leerer `Main`, ADRs.
   - Test: `./mvnw -q verify` ist grün, ein Smoke-Test lädt `Main`.

2. **Spike: Neo4j-Input-Vertrag absichern** (Test-only)
   - Inhalt: ein minimales `Input` über In-Memory-Listen. Die DB wird zur Prüfung mit `DatabaseManagementServiceBuilder` geöffnet.
   - Test 1: `nodes()` und `relationships()` werden je genau **einmal** geöffnet, und alle Knoten kommen vor der ersten Kante.
   - Test 2: Eine Kante auf einen Knoten, der in der Eingabe später steht, wird importiert.
   - Test 3: Ein fehlendes Ziel landet im Error-Log-Stream.

3. **CLI-Argumente** (`cli`)
   - Inhalt: `<json-file>` und `<pfad>`, `--compression=auto|none|gzip|bzip2` (Standard `auto` per Dateiendung), `--error-log`, `--chunk-size`. Validierung: Datei existiert, Ziel fehlt oder ist leer.
   - Test: `CommandLine.execute(...)` prüft Exit-Codes, Fehlermeldungen und die Optionswerte.

4. **DumpSource + LineChunkReader** (`dump`)
   - Test 1: Das Fixture liefert 99 Records, der erste ist Q31. `.gz` und `.bz2` liefern dasselbe Ergebnis.
   - Test 2: `auto` erkennt das Format an der Dateiendung, ein explizites Flag überschreibt die Endung.
   - Test 3: Mehrere Threads lesen Chunks und bekommen jede Zeile genau einmal.
   - Die Fixtures `.gz` und `.bz2` liegen in `src/test/resources`.

5. **Model + EntityParser** (`model`, `parse`)
   - Test: Q31 ergibt Label `Belgium`, die Beschreibung, Aliases und eine erwartete Anzahl Statements je Datentyp.
   - Zusätzlich: ein neues Fixture `mixed.json`, in dem Properties (P31, P1082, P625) **nach** den Items stehen und ein Verweis auf eine nicht enthaltene Q-Id vorkommt.

6. **Naming** (`mapping.Naming`)
   - Test: parametrisierte Fälle wie `instance of` → `INSTANCE_OF`, `globe-coordinate` → `GlobeCoordinate`, `2D` → `_2D`.

7. **NodeMapper + PropertyIndex** (`mapping`)
   - Test 1: Für Q31 stimmen der Entity-Knoten (Labels und Felder) und die Claim-Knoten (`:Claim:Quantity` …, Id = Statement-Id).
   - Test 2: Ein Property-Record trägt seinen Typ in den Index ein (`P31` → `INSTANCE_OF`). Unbekannte Ids ergeben `CLAIM`.

8. **RelationshipMapper** (`mapping`)
   - Test: Für Q31 entsteht Q31 -[INSTANCE_OF {by:P31, id}]-> Q6256. Die Claim-Kanten zeigen auf dieselben Statement-Ids wie in Schritt 7. `UNIT_TYPE` und `GLOBE_TYPE` werden erzeugt.

9. **WikidataInput + ImporterRunner** (`neo4j`)
   - Inhalt: Node- und Relationship-InputIterator auf Basis des LineChunkReaders. `validateAndEstimate` schätzt grob aus der Dateigröße.
   - Test 1: Import von `mixed.json` in `@TempDir`. `INSTANCE_OF` ist vorhanden, obwohl P31 erst nach den Items steht, und Vorwärtsverweise werden aufgelöst.
   - Test 2: Der Verweis auf die fehlende Q-Id steht im Error-Log, und der Import läuft durch.
   - Test 3: Die Datei wurde genau zweimal geöffnet (über eine zählende DumpSource).

10. **Orchestrator + CLI End-to-End**
    - Test: `wikidata-100.json` und `mixed.json` laufen per CLI in `@TempDir`. Die DB öffnet sich, `count(n) > 0` gilt, und die Labels `Item` und `Claim` sowie mindestens eine `INSTANCE_OF`-Kante sind vorhanden. Das entspricht der Akzeptanz aus SPEC.

11. **Packaging + Shellscript**
    - Inhalt: Shade baut `tools.jar`, dazu `bin/wikidata-import.sh <json-file> <pfad> [optionen]`.
    - Test: ein Failsafe-IT ruft das Script mit dem `.bz2`-Fixture auf und prüft Exit-Code 0 sowie eine nicht leere DB.

## Verifikation
- Nach jedem Schritt laufen der Einzeltest (`./mvnw -q test -Dtest=…`) und danach `./mvnw -q verify`.
- Zum Schluss wird `bin/wikidata-import.sh` manuell mit dem Fixture ausgeführt und die DB in Neo4j 5.26 geöffnet.
