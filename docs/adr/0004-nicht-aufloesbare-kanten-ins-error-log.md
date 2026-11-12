# 4. Nicht auflösbare Kanten ins Error-Log

Status: akzeptiert

## Kontext
Eine Kante kann auf eine Entity zeigen, die im Dump nicht vorkommt, etwa bei gelöschten Items oder Teil-Dumps. Der Import darf deshalb nicht abbrechen (Akzeptanzkriterium in SPEC.md). Verweise auf Entities weiter unten in der Datei sind kein Problem, weil alle Knoten vor der ersten Kante feststehen.

## Entscheidung
Nicht auflösbare Kanten werden vom Neo4j-`Collector` (`Collectors.badCollector` ohne Fehlergrenze) in eine Datei geschrieben, die per `--error-log` gewählt wird. Standard ist `<pfad>.errors.log`. Am Ende gibt das Tool Anzahl und Pfad aus.

## Alternativen
- Platzhalter-Knoten nur mit `id` anlegen. Verworfen, weil dafür vorab alle Ziele bekannt sein müssten.
- Abbruch mit Fehler. Verworfen wegen des Akzeptanzkriteriums.

## Konsequenzen
- Die Datenbank enthält keine Kanten ins Leere.
- Verlorene Kanten bleiben im Log nachvollziehbar.
