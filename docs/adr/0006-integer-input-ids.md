# 6. Integer input ids

Status: accepted

## Context
With `IdType.STRING` the batch importer hashes every input id, writes every id into a temporary property store and reads them back in `prepareIdMapper()` to resolve hash collisions. Claim node ids were Wikidata statement GUIDs of about 40 characters, so each claim node also needed dynamic string records in that temporary store. The full dump has hundreds of millions of claim nodes.

## Decision
The importer runs with `IdType.INTEGER`. The mappers keep readable string keys, `InputIds` encodes them into non-negative longs below 2^56 (the limit Neo4j accepts):

- Items and properties: kind in bits 55..54, Wikidata number in bits 53..0.
- Claim nodes: key `<entity>#<statement position>`, e.g. `Q42#7`. Bits 55..54 mark a claim, bit 53 the owner kind, bits 52..20 the owner number, bits 19..0 the position of the statement within the entity.

The statement position is stable between both passes because both parse the same line in the same way. The statement GUID stays on the claim node as `id` property. `ReadableIdCollector` decodes ids before they reach the error log. A relationship whose target cannot be encoded goes to the error log as well.

## Consequences
- On a synthetic dump with 3 million entities (9M nodes, 12M relationships) node import was 21% and relationship import 23% faster, the total import 17%. The store got smaller (3.6 GB instead of 4.1 GB).
- Limits: entity numbers up to 2^33 for claim owners (Q ids are around 1.4 * 10^8 today), up to 2^20 statements per entity.
- Error log entries for claims show `Q42#7` instead of the statement GUID.
