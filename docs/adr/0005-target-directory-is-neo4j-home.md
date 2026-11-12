# 5. Target directory is the Neo4j home

Status: accepted

## Context
SPEC.md requires the created directory to be a valid, non-empty Neo4j database. Neo4j places the database and the transaction logs in separate locations below `data/`.

## Decision
The second argument `<path>` is the Neo4j home. The database `neo4j` is then located in `<path>/data/databases/neo4j`, the transaction logs in `<path>/data/transactions/neo4j`. The directory must not exist or must be empty.

## Consequences
- The result can be opened directly with `DatabaseManagementServiceBuilder(<path>)` or used as `NEO4J_HOME` for a server.
- The error log by default lies next to the target directory (`<path>.errors.log`) so that the home stays empty.
