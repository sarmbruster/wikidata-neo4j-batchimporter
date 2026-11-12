# 1. Neo4j 5 with the internal BatchImporter

Status: accepted

## Context
The database is to be created on the Java level with the Neo4j batch importer. The old `BatchInserter` API no longer exists as of Neo4j 5.

## Decision
Neo4j 5.26 (LTS, Community) with the internal `org.neo4j.internal.batchimport.BatchImporter`, created via the `BatchImporterFactory`. Input ids are encoded integers (`IdType.INTEGER`), see [ADR 0006](0006-integer-input-ids.md).

## Consequences
- The API is internal and may change between Neo4j versions. The Neo4j version is therefore pinned in `pom.xml`.
- Neo4j 5.26 is designed for Java 21, hence `release 21`.
- A spike test secures the contract of the importer (one pass each over nodes and relationships, all nodes before the first relationship).
