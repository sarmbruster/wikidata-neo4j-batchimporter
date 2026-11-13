# Project
See @SPEC.md for requirements. Invocation, options and graph model are described in @README.md, decisions in `docs/adr/`.

## Commands
- Build and tests (including the integration tests `*IT`, which need the built `target/tools.jar`): `./mvnw -q verify`
- Single test: `./mvnw -q test -Dtest=NodeMapperTest`
- `*IT` tests run only with `verify`, not with `test`

## Architecture
- The Neo4j batch importer (internal API, version pinned in `pom.xml`, 5.26.x) processes all nodes first, then all relationships. The tool therefore reads the dump twice (ADR 0003): pass 1 delivers nodes and fills the `PropertyIndex` (property id → relationship type), pass 2 delivers relationships with the complete index.
- Do not add intermediate buffers or spool files: a buffer for relationships blocks the reader during the node phase. The importer pulls chunks from multiple threads (`LineChunkReader` reads under a lock, parsing and mapping then run in parallel).
- Data flow: `dump` (lines) → `parse` (entity) → `mapping` (`NodeMapper`, `RelationshipMapper`) → `neo4j` (`WikidataInput`, `ImporterRunner`). `WikidataImport` connects everything, `cli` is only picocli.
- `<path>` is the Neo4j home, not the database directory (ADR 0005): the database is located in `<path>/data/databases/neo4j`.
- Relationships to entities that are missing in the dump end up in the error log and do not abort the import (ADR 0004).
- Progress (ADR 0007): `ImporterRunner` injects `StageProgressMonitor` (an `ExecutionMonitor`) next to Neo4j's own report; `ProgressTracker` measures the two dump passes in bytes and the later phases from the stage events.
- Input ids are `IdType.INTEGER` (ADR 0006): the mappers use string keys (`Q42`, `P31`, claim nodes `Q42#7` = entity + statement position), `InputIds` encodes them into 56 bits, `ReadableIdCollector` decodes them for the error log.

## Tests
- `Fixtures` and `Neo4jHome` (test classes) load fixtures and open the created database and count with Cypher, respectively.
- `wikidata-100.json` is 20 MB (items only): gz and bz2 variants are created at runtime in the test and are not checked in.
- `mixed.json` is the small fixture (properties after the items, forward reference, reference to the missing `Q404404`) for mapping and import tests.

## Rules
- English is the project language: code, documentation, ADRs and commit messages
- Run `./mvnw -q verify` after every change, report done only when green
- No new dependencies without asking
- Records instead of Lombok, no field injection
- Tests with AssertJ, fixtures from src/test/resources
