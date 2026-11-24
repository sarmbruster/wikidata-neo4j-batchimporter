# 2. Graph model after findie/wikidata-neo4j-importer

Status: accepted

## Context
The model of the Wikidata graph in Neo4j has to be defined.

## Decision
We adopt the model of https://github.com/findie/wikidata-neo4j-importer:

- `:Entity:Item` and `:Entity:Property` with `id`, `label`, `slug`, `descriptions[]`, `aliases[]` (English only). Properties additionally carry `datatype`.
- Claims of type `wikibase-item` and `wikibase-property` become relationships `(Entity)-[:<PROPERTY_LABEL> {by, id}]->(Entity)`. The relationship type is the label of the property in upper case, the fallback is `CLAIM`.
- All other datatypes become `:Claim:<Datatype>` nodes, attached via `(Entity)-[:<PROPERTY_LABEL> {by}]->(Claim)`.
- `:Quantity` and `:GlobeCoordinate` get `UNIT_TYPE` and `GLOBE_TYPE` respectively to the target entity. The fields `unit` and `globe` are dropped in the process.

Qualifiers, references, sitelinks, lexemes and snaks with `snaktype` other than `value` are not adopted.

## Consequences
- The claim node carries the statement id in its `id` property. During the import it is identified by its owning entity and the position of the statement ([ADR 0006](0006-integer-input-ids.md)).
- The graph stays considerably smaller than with a complete model.
- `unit` and `globe` are removed already when the claim node is created if they contain an entity URL (start with `http`). Unlike in findie, it is not yet known in the first pass whether the target exists. If it is missing in the dump, the value on the node is lost, but remains traceable through the entry in the error log. Values such as `unit: "1"` are kept.
- `id` on a claim node is reserved for the statement id and is never overwritten by a field of the value. Values that reference entity kinds outside the dump (lexemes, forms, senses, entity schemas, all `wikibase-entityid` values other than items and properties) are reduced to `value: <id>`; `entity-type` and `numeric-id` are dropped. Before this rule the referenced id replaced the statement id, so e.g. all claims pointing to `E206` had `id: 'E206'`. Databases imported before the fix have to be re-imported.
