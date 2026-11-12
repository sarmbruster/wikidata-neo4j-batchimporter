# SPEC
## Goal
CLI tool: reads a Wikidata JSON file and uses the Neo4j batch importer on the Java level to create a Neo4j database.
## Stack
Java 21, Maven, picocli, Neo4j, JUnit, AssertJ
## Format details
JSON array, each record is on its own line, details https://doc.wikimedia.org/Wikibase/master/php/docs_topics_json.html
## Non-goals
No GUI
## Invocation
Shell script that internally calls `java -jar tools.jar <json-file> <path>`.
## Acceptance
The batch importer does not abort, the created directory is a valid Neo4j database that is not empty.
