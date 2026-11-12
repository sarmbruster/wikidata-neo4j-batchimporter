# 4. Unresolvable relationships to the error log

Status: accepted

## Context
A relationship can point to an entity that does not occur in the dump, for example for deleted items or partial dumps. The import must therefore not abort (acceptance criterion in SPEC.md). References to entities further down in the file are not a problem, because all nodes are known before the first relationship.

## Decision
Unresolvable relationships are written by the Neo4j `Collector` (`Collectors.badCollector` without an error limit) to a file selected via `--error-log`. The default is `<path>.errors.log`. At the end the tool prints the count and the path.

## Alternatives
- Create placeholder nodes with only an `id`. Rejected because all targets would have to be known in advance.
- Abort with an error. Rejected because of the acceptance criterion.

## Consequences
- The database contains no relationships into the void.
- Lost relationships remain traceable in the log.
