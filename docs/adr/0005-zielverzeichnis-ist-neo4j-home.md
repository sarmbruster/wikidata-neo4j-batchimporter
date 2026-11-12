# 5. Zielverzeichnis ist das Neo4j-Home

Status: akzeptiert

## Kontext
SPEC.md verlangt, dass das erzeugte Verzeichnis eine valide, nicht leere Neo4j-Datenbank ist. Neo4j legt Datenbank und Transaktionslogs an getrennten Orten unterhalb von `data/` ab.

## Entscheidung
Das zweite Argument `<pfad>` ist das Neo4j-Home. Die Datenbank `neo4j` liegt danach unter `<pfad>/data/databases/neo4j`, die Transaktionslogs unter `<pfad>/data/transactions/neo4j`. Das Verzeichnis darf nicht existieren oder muss leer sein.

## Konsequenzen
- Das Ergebnis lässt sich direkt mit `DatabaseManagementServiceBuilder(<pfad>)` öffnen oder als `NEO4J_HOME` für einen Server verwenden.
- Der Error-Log liegt standardmäßig neben dem Zielverzeichnis (`<pfad>.errors.log`), damit das Home leer bleibt.
