# 8. Explicit off-heap memory budget

Status: accepted

## Context
The first full import on a 64 GB machine linked relationships one type per round: 13,463 rounds of about one minute each, because every round scans the whole relationship store (63 GB). Linking may use the off-heap budget (`Configuration.maxOffHeapMemory()`) minus the node cache (about 10 GB for one billion nodes) and the store memory.

Neo4j computes the default budget once at the start of the import as 90 % of the free physical memory minus the JVM heap (`ToolingMemoryCalculations`). The free memory is `MemFree`, which excludes the file system cache, and the default heap is a quarter of the RAM. On that machine little was left for linking.

We passed `Monitor.NO_MONITOR` to the importer, so Neo4j's warnings about insufficient memory or an oversized heap were dropped.

## Decision
- `--max-off-heap-memory` sets the budget as an absolute size (`40g`) or as a percentage of `MemAvailable` minus the heap. `ImporterRunner` passes it through `Configuration.Overridden`.
- The default stays `90%`, but of `MemAvailable` instead of `MemFree`.
- `LoggingImportMonitor` writes the warnings of Neo4j's sanity checks to the log file and to stderr. The chosen budget is logged at the start.

## Consequences
- On Linux the default budget no longer depends on how full the file system cache is. On other systems it falls back to the free physical memory as reported by the JVM.
- Too large a budget can make the operating system swap or kill the process. The README recommends a small heap and an explicit size.
- Neo4j's own heap sanity check still uses `MemFree`, so its warnings can be pessimistic on Linux.
