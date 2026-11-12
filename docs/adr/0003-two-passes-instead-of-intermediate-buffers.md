# 3. Two passes instead of intermediate buffers

Status: accepted

## Context
The importer works sequentially: `importNodes()`, `prepareIdMapper()`, `importRelationships()`. Relationships are requested only after all nodes have been imported. While reading the dump, however, nodes and relationships arise interleaved. A ring buffer for relationships would fill up in the node phase and block the reader. In addition, the relationship type depends on the label of the property, which may also appear far down in the dump.

## Decision
The dump file is read twice:

- `Input.nodes()` delivers only nodes in the first pass and fills the PropertyIndex (property id to relationship type) in the process.
- `Input.relationships()` delivers only relationships in the second pass, with the then complete PropertyIndex.

The importer pulls chunks from multiple threads. Only a few raw lines are read under a lock, parsing and mapping then run in parallel. This yields backpressure without an intermediate buffer.

## Alternatives
- One pass with a ring buffer for nodes and a spool file for relationships. Rejected because of the additional disk space (several tens of GB) and the complexity.

## Consequences
- The dump is read and decompressed twice. For bz2, decompression is the bottleneck.
- Memory use stays limited to a few chunks per thread.
