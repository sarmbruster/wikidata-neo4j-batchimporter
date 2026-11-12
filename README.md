# wikidata-neo4j-batchimporter

CLI tool that reads a Wikidata JSON dump and writes a new Neo4j 5 database with the Neo4j batch importer (Java API, no Cypher, no running server). The graph model follows [findie/wikidata-neo4j-importer](https://github.com/findie/wikidata-neo4j-importer).

## Prerequisites

- Java 21+ (built with `--release 21`, tests run on JDK 25)
- A Wikidata JSON dump: a JSON array with one entity per line, optionally compressed (`.json`, `.gz`, `.bz2`). See the [dump format](https://doc.wikimedia.org/Wikibase/master/php/docs_topics_json.html).

## Build

```
./mvnw -q verify
```

Runs unit and integration tests and builds the executable `target/tools.jar`. Use `./mvnw -q package -DskipTests` to build without tests.

## Run

```
bin/wikidata-import.sh <json-file> <path> [options]
```

The script calls `java -jar tools.jar` and looks for `tools.jar` next to itself, then in `../target/`. Pass JVM options through `JAVA_OPTS`:

```
JAVA_OPTS="-Xmx8g" bin/wikidata-import.sh latest-all.json.bz2 /data/neo4j-home --progress
```

`<path>` is used as Neo4j home. It must not exist or must be empty. The database is created in `<path>/data/databases/neo4j` and opens with an embedded `DatabaseManagementServiceBuilder(<path>)` or as `NEO4J_HOME` of a Neo4j 5 server.

| Option | Default | Description |
|---|---|---|
| `--compression=auto\|none\|gzip\|bzip2` | `auto` | `auto` decides by file extension (`.gz`, `.bz2`). |
| `--error-log=<file>` | `<path>.errors.log` | Receives relationships that cannot be resolved. |
| `--chunk-size=<lines>` | `1000` | Dump lines read per chunk by each importer thread. |
| `--progress` | off | Progress bar with elapsed time and ETA on stderr. Replaces the Neo4j progress output. |

Exit codes: `0` success, `1` import failed, `2` invalid arguments.

## Graph model

| Source | Result |
|---|---|
| Item, property | `(:Entity:Item)` / `(:Entity:Property)` with `id`, `label`, `slug`, `descriptions`, `aliases` (English only); properties also have `datatype` |
| Statement with item or property value | `(Entity)-[:<PROPERTY_LABEL> {by, id}]->(Entity)`; `PROPERTY_LABEL` is the upper-cased English label of the property, `CLAIM` if unknown |
| Statement with any other value | `(Entity)-[:<PROPERTY_LABEL> {by}]->(:Claim:<Datatype>)`, e.g. `:Claim:Quantity`; the statement id is the claim node id |
| Quantity unit, globe coordinate globe | `(:Quantity)-[:UNIT_TYPE]->(Entity)`, `(:GlobeCoordinate)-[:GLOBE_TYPE]->(Entity)`; `unit` / `globe` are removed from the node when they hold an entity URL |

Qualifiers, references, sitelinks, lexemes and statements whose snak type is not `value` are ignored. Rationale in [docs/adr](docs/adr).

## How it works

The Neo4j importer consumes all nodes before it asks for the first relationship. The tool therefore reads the dump twice instead of buffering relationships:

```mermaid
flowchart LR
    D[(Dump)] -->|pass 1| N[Nodes + property index]
    N --> I[Neo4j batch importer]
    D -->|pass 2| R[Relationships]
    R --> I
    I --> DB[(Neo4j database)]
```

- Pass 1 emits entity and claim nodes and collects `property id -> relationship type`.
- Pass 2 emits relationships using the complete property index, so properties may appear anywhere in the dump.
- The importer pulls chunks of raw lines from multiple threads. Parsing and mapping run outside the read lock. Memory use is bounded by `threads x chunk size`.
- A relationship whose target is missing in the dump goes to the error log. The import continues.

## Limitations

- The ETA covers reading the dump only. Neo4j preparation between the passes and the final linking steps add to the total time.
- The import size estimates passed to Neo4j are derived from the file size and not tuned against a full dump.
