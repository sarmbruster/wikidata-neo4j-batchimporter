# 7. Stage-aware progress via an own ExecutionMonitor

Status: accepted

## Context
The progress bar measured only the bytes read from the dump. On the full dump reading took about 4 hours. The bar then stayed at 100 % for more than 9 hours while Neo4j linked relationships and built the auxiliary stores.

The batch importer reports its stages to an `ExecutionMonitor`. `StorageEngineFactory.batchImporter(...)` creates that monitor itself from a `PrintStream` and offers no way to add another one. The public `Monitor` only exposes one overall percentage in 1 % steps, weighted from the input estimates and without stage names.

## Decision
- `ImporterRunner` instantiates the importer through `BatchImporterFactory.withHighestPriority()` with a `MultiExecutionMonitor`. It combines Neo4j's `HumanUnderstandableExecutionMonitor` (the stage report on stdout and in the log file) with our `StageProgressMonitor`.
- `StageProgressMonitor` maps the stages to the four phases of Neo4j's report: node import, relationship import, linking and post processing. For linking and post processing it computes goals and progress the same way as `HumanUnderstandableExecutionMonitor`.
- The two dump passes keep the byte-based progress. It is exact, while Neo4j's goal for these phases is derived from our size estimates.
- `neo4j-record-storage-engine`, already a runtime dependency of `neo4j`, becomes a direct compile dependency.

## Consequences
- The bar shows the phase, the running Neo4j stage including the type range of a linking round, and an ETA for the current phase.
- More internal API is used: stage names, `StageExecution`, step statistics, `BatchingNeoStores` and `DataStatistics`. The Neo4j version stays pinned ([ADR 0001](0001-neo4j-5-internal-batchimporter.md)). `StageProgressMonitorTest` breaks if the stage names or their order change.
- Linking progress counts linked relationships. When linking needs several rounds, every round scans the whole relationship store, so the percentage advances more slowly in later rounds than the time spent.
