# Plan: Package structure and implementation steps (wikidata-neo4j-batchimporter)

## Context
So far the repo is empty except for `SPEC.md`, `CLAUDE.md` and the fixture `src/test/resources/wikidata-100.json` (99 items, no properties, one record per line, enclosed in `[` / `]`).
Goal according to SPEC: a CLI `java -jar tools.jar <json-file> <path>` (called via a shell script) creates a valid, non-empty Neo4j database with the Neo4j batch importer on the Java level.

Decisions made:
- Neo4j 5.x Community (5.26 LTS)
- Graph model as in findie/wikidata-neo4j-importer
- Base package `de.thm.mni.wikidata.neo4j`
- Dependencies Jackson and commons-compress (bz2), fat JAR via maven-shade-plugin
- Unresolvable relationships go to the error log.
- **The file is read twice:** only nodes in the first pass, only relationships in the second. There are no large intermediate buffers.

## Architecture: two passes, pulled directly by the importer

The Neo4j importer works sequentially: `importNodes()` → `prepareIdMapper()` → `importRelationships()`. This was verified in the source code `ParallelBatchImporter.doImport` (5.25/dev). The design follows from that:

- **`Input.nodes()`** opens the dump and delivers nodes only:
  - `:Entity:Item` and `:Entity:Property`
  - `:Claim:<Datatype>` nodes for claims without an entity target
- **`Input.relationships()`** opens the dump a second time and delivers relationships only:
  - Entity → Entity
  - Entity → Claim
  - `UNIT_TYPE` and `GLOBE_TYPE`
- **No intermediate buffer:** The importer pulls from multiple threads via `InputIterator.next(InputChunk)`.
  - Only one chunk of raw lines is read under a lock, e.g. 1,000 lines. JSON parsing and mapping then run without a lock in the respective importer thread.
  - At most "number of threads × chunk" lines are therefore held in memory.
  - Backpressure arises by itself: lines are read only when the importer requests a new chunk. There is no ring buffer and no spool.
- **PropertyIndex:** a mapping of property id → relationship type.
  - It is filled as a side effect in the first pass. That is about 12,000 entries in a `ConcurrentHashMap`.
  - In the second pass it is complete, even if properties appear late in the dump.
- **Claim nodes and claim relationships:** Both passes produce the same claim ids, namely the Wikidata statement ids. The relationships therefore find their claim nodes via the importer's string ids.
- **Unresolvable relationships:** `Collectors.badCollector(OutputStream, UNLIMITED_TOLERANCE)` writes them to `--error-log` (default `<path>.errors.log`). The import does not abort. Relationships to entities that appear further down in the file are resolved normally, because all nodes are known before the first relationship.
- **Cost:** The dump is read and decompressed twice. For bz2, decompression is the bottleneck. That is the price for having no intermediate file.

## Package structure (`de.thm.mni.wikidata.neo4j`)
```
de.thm.mni.wikidata.neo4j
├── cli        WikidataImportCommand (picocli), Main – arguments, exit codes
├── dump       DumpSource (opens file, Compression AUTO|NONE|GZIP|BZIP2),
│              LineChunkReader – reads n raw lines under a lock, strips [ ] and commas
├── model      Records: Entity, EntityType, Statement, DataValue (sealed: EntityRef, Quantity,
│              GlobeCoordinate, Time, Monolingualtext, StringValue, ...)
├── parse      EntityParser – JSON line → Entity (Jackson, en only, mainsnak/value only)
├── mapping    Naming (slugify/relationify/labelify), PropertyIndex (thread-safe),
│              NodeMapper – Entity → nodes; RelationshipMapper – Entity + PropertyIndex → relationships
├── neo4j      WikidataInput (implements Input), NodeInputIterator / RelationshipInputIterator
│              (each on top of LineChunkReader), ImporterRunner (BatchImporterFactory, Config, Collector)
└── WikidataImport   Orchestrator
```
Further files: `bin/wikidata-import.sh` and ADRs in `docs/adr/`:
- 0001: Neo4j 5 with the internal BatchImporter
- 0002: Graph model after findie
- 0003: Two passes instead of intermediate buffers
- 0004: Unresolvable relationships to the error log

## Implementation steps (each individually testable)

1. **Project skeleton**
   - Content: Maven wrapper, `pom.xml` (Java 21, picocli, neo4j 5.26, jackson-databind, commons-compress, JUnit, AssertJ, shade), empty `Main`, ADRs.
   - Test: `./mvnw -q verify` is green, a smoke test loads `Main`.

2. **Spike: secure the Neo4j input contract** (test only)
   - Content: a minimal `Input` over in-memory lists. The DB is opened with `DatabaseManagementServiceBuilder` for verification.
   - Test 1: `nodes()` and `relationships()` are each opened exactly **once**, and all nodes come before the first relationship.
   - Test 2: A relationship to a node that appears later in the input is imported.
   - Test 3: A missing target ends up in the error log stream.

3. **CLI arguments** (`cli`)
   - Content: `<json-file>` and `<path>`, `--compression=auto|none|gzip|bzip2` (default `auto` by file extension), `--error-log`, `--chunk-size`. Validation: file exists, target does not exist or is empty.
   - Test: `CommandLine.execute(...)` checks exit codes, error messages and option values.

4. **DumpSource + LineChunkReader** (`dump`)
   - Test 1: The fixture delivers 99 records, the first is Q31. `.gz` and `.bz2` deliver the same result.
   - Test 2: `auto` detects the format by file extension, an explicit flag overrides the extension.
   - Test 3: Multiple threads read chunks and each line is received exactly once.
   - The `.gz` and `.bz2` fixtures are located in `src/test/resources`.

5. **Model + EntityParser** (`model`, `parse`)
   - Test: Q31 yields the label `Belgium`, the description, aliases and an expected number of statements per datatype.
   - In addition: a new fixture `mixed.json` in which properties (P31, P1082, P625) appear **after** the items and a reference to a Q id that is not contained occurs.

6. **Naming** (`mapping.Naming`)
   - Test: parameterized cases such as `instance of` → `INSTANCE_OF`, `globe-coordinate` → `GlobeCoordinate`, `2D` → `_2D`.

7. **NodeMapper + PropertyIndex** (`mapping`)
   - Test 1: For Q31 the entity node (labels and fields) and the claim nodes (`:Claim:Quantity` …, id = statement id) are correct.
   - Test 2: A property record registers its type in the index (`P31` → `INSTANCE_OF`). Unknown ids yield `CLAIM`.

8. **RelationshipMapper** (`mapping`)
   - Test: For Q31 the relationship Q31 -[INSTANCE_OF {by:P31, id}]-> Q6256 is created. The claim relationships point to the same statement ids as in step 7. `UNIT_TYPE` and `GLOBE_TYPE` are created.

9. **WikidataInput + ImporterRunner** (`neo4j`)
   - Content: node and relationship InputIterator based on the LineChunkReader. `validateAndEstimate` estimates roughly from the file size.
   - Test 1: Import of `mixed.json` into `@TempDir`. `INSTANCE_OF` is present although P31 appears after the items, and forward references are resolved.
   - Test 2: The reference to the missing Q id is in the error log, and the import runs through.
   - Test 3: The file was opened exactly twice (via a counting DumpSource).

10. **Orchestrator + CLI end to end**
    - Test: `wikidata-100.json` and `mixed.json` run via CLI into `@TempDir`. The DB opens, `count(n) > 0` holds, and the labels `Item` and `Claim` as well as at least one `INSTANCE_OF` relationship are present. This corresponds to the acceptance criterion in SPEC.

11. **Packaging + shell script**
    - Content: Shade builds `tools.jar`, plus `bin/wikidata-import.sh <json-file> <path> [options]`.
    - Test: a Failsafe IT calls the script with the `.bz2` fixture and checks exit code 0 and a non-empty DB.

## Verification
- After each step the single test (`./mvnw -q test -Dtest=…`) runs, followed by `./mvnw -q verify`.
- Finally `bin/wikidata-import.sh` is run manually with the fixture and the DB is opened in Neo4j 5.26.
