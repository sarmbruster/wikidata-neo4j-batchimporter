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
JAVA_OPTS="-Xmx4g" bin/wikidata-import.sh latest-all.json.gz /data/neo4j-home --progress --max-off-heap-memory=40g
```

`<path>` is used as Neo4j home. It must not exist or must be empty. The database is created in `<path>/data/databases/neo4j` and opens with an embedded `DatabaseManagementServiceBuilder(<path>)` or as `NEO4J_HOME` of a Neo4j 5 server.

| Option | Default | Description |
|---|---|---|
| `--compression=auto\|none\|gzip\|bzip2` | `auto` | `auto` decides by file extension (`.gz`, `.bz2`). |
| `--error-log=<file>` | `<path>.errors.log` | Receives relationships that cannot be resolved. |
| `--chunk-size=<lines>` | `1000` | Dump lines read per chunk by each importer thread. |
| `--log-file=<file>` | none | Log with UTC timestamps: start and end of the import, the Neo4j import stages with estimates, durations and peak memory, Neo4j's internal messages and the stack trace of a failure. |
| `--max-off-heap-memory=<size>` | `90%` | Memory for Neo4j's importer caches outside the JVM heap: a size such as `40g` or a percentage (1–99) of the available memory (Linux `MemAvailable`) minus the heap. See [Memory](#memory). |
| `--progress` | off | Progress bar on stderr per import phase, with the running Neo4j stage, total elapsed time and an ETA for the current phase. Replaces the Neo4j progress output on stdout; with `--log-file` that output still goes to the log. |

Exit codes: `0` success, `1` import failed, `2` invalid arguments.

The progress bar follows the four phases of the Neo4j importer:

```
[#############-----------------]  43.2%  3/4 Relationship linking (Relationship <-- Relationship 1-38/9100)  elapsed 6h 12m  phase ETA 2h 05m
```

| Phase | Progress measured by |
|---|---|
| 1/4 Node import | bytes read in pass 1; `Prepare node index` follows at 100 % |
| 2/4 Relationship import | bytes read in pass 2 |
| 3/4 Relationship linking | relationships linked, as reported by Neo4j |
| 4/4 Post processing | relationship groups and records processed, as reported by Neo4j |

Linking runs in rounds when the relationship types do not fit into memory at once. The stage shows the type range of the round (`1-38/9100`). Every round scans the whole relationship store, so later rounds with few relationships take longer than their share of the percentage suggests.

## Memory

Most of the importer's memory lives outside the JVM heap: the id mapper during the two passes, and during linking the node cache (10 bytes per node) plus the relationship groups of dense nodes. Linking processes as many relationship types per round as fit into the off-heap budget left after the node cache. Every round scans the whole relationship store, so a small budget turns linking into thousands of rounds (one per type in the worst case).

- Keep the heap small (`-Xmx4g` is enough) so the remaining RAM is available off-heap.
- Set `--max-off-heap-memory` explicitly, e.g. `40g` on a 64 GB machine. Neo4j's own default takes 90 % of `MemFree`, which excludes the file system cache and is small on a machine that has just read a large file.
- The log file records the chosen budget at the start, and the warnings of Neo4j's memory and heap sanity checks are written to the log and stderr.

## Release

Push a tag `vX.Y.Z` (or `vX.Y.Z-rc1` for a pre-release) to publish a GitHub release:

```
git tag v1.0.0
git push origin v1.0.0
```

The workflow runs the tests and attaches a ZIP with `tools.jar`, `wikidata-import.sh` and a SHA-256 file. See [docs/releasing.md](docs/releasing.md) for the full process.

## Graph model

| Source | Result |
|---|---|
| Item, property | `(:Entity:Item)` / `(:Entity:Property)` with `id`, `label`, `slug`, `descriptions`, `aliases` (English only); properties also have `datatype` |
| Statement with item or property value | `(Entity)-[:<PROPERTY_LABEL> {by, id}]->(Entity)`; `PROPERTY_LABEL` is the upper-cased English label of the property, `CLAIM` if unknown |
| Statement with any other value | `(Entity)-[:<PROPERTY_LABEL> {by}]->(:Claim:<Datatype>)`, e.g. `:Claim:Quantity`; the `id` property holds the statement id |
| Statement whose value references a lexeme, form, sense or entity schema | `:Claim:<Datatype>` node as above with the referenced id in `value`, e.g. `(:Claim:EntitySchema {id: 'Q42$…', value: 'E206'})` |
| Quantity unit, globe coordinate globe | `(:Quantity)-[:UNIT_TYPE]->(Entity)`, `(:GlobeCoordinate)-[:GLOBE_TYPE]->(Entity)`; `unit` / `globe` are removed from the node when they hold an entity URL |

Qualifiers, references, sitelinks, lexemes and statements whose snak type is not `value` are ignored. Rationale in [docs/adr](docs/adr).

## Indexes and constraints

The Neo4j record format importer cannot create schema (`Input.schemaCommands()` is rejected), so the database only contains the label and relationship type lookup indexes. Create the indexes and constraints after the import, once the database is running:

```cypher
CREATE RANGE INDEX FOR (n:Entity) ON (n.label);
CREATE RANGE INDEX FOR (n:GlobeCoordinate) ON (n.globe);
CREATE RANGE INDEX FOR (n:Quantity) ON (n.unit);
CREATE TEXT INDEX FOR (n:Entity) ON (n.label);
CREATE CONSTRAINT claim_id FOR (node:Claim) REQUIRE (node.id) IS UNIQUE;
CREATE CONSTRAINT entity_id FOR (node:Entity) REQUIRE (node.id) IS UNIQUE;
```

Every `:Item` and `:Property` is also an `:Entity`, so `entity_id` covers their ids; match on `:Entity` to use it.

For example with `cypher-shell -f schema.cypher`. `CREATE INDEX` returns immediately and Neo4j populates the index in the background; wait with `CALL db.awaitIndexes()` or watch `SHOW INDEXES` until every index is `ONLINE`. `CREATE CONSTRAINT` blocks until its backing index is populated and fails if the data contains duplicate ids.

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
- Node ids are passed to Neo4j as encoded integers instead of strings, which avoids string hashing and a temporary id store.
- A relationship whose target is missing in the dump goes to the error log. The import continues. Claim nodes appear there as `<entity>#<statement position>`, e.g. `Q100#3`.

## Limitations

- The ETA covers the current phase only. With several linking rounds it is too optimistic during phase 3.
- The import size estimates passed to Neo4j are derived from the file size and not tuned against a full dump.
