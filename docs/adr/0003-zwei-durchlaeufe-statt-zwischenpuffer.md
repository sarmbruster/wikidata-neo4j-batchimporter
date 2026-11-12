# 3. Zwei Durchläufe statt Zwischenpuffer

Status: akzeptiert

## Kontext
Der Importer arbeitet sequenziell: `importNodes()`, `prepareIdMapper()`, `importRelationships()`. Kanten werden erst abgeholt, wenn alle Knoten importiert sind. Beim Lesen des Dumps entstehen Knoten und Kanten aber gemischt. Ein Ringpuffer für Kanten würde in der Knotenphase volllaufen und den Leser blockieren. Außerdem hängt der Kantentyp vom Label der Property ab, die auch weit hinten im Dump stehen kann.

## Entscheidung
Die Dump-Datei wird zweimal gelesen:

- `Input.nodes()` liefert im ersten Durchlauf nur Knoten und füllt dabei den PropertyIndex (Property-Id zu Kantentyp).
- `Input.relationships()` liefert im zweiten Durchlauf nur Kanten, mit dem dann vollständigen PropertyIndex.

Der Importer zieht Chunks aus mehreren Threads. Gelesen werden unter einem Lock nur wenige Rohzeilen, Parsing und Mapping laufen danach parallel. Daraus ergibt sich Backpressure ohne Zwischenpuffer.

## Alternativen
- Ein Durchlauf mit Ringpuffer für Knoten und Spool-Datei für Kanten. Verworfen wegen des zusätzlichen Speicherplatzes (einige zehn GB) und der Komplexität.

## Konsequenzen
- Der Dump wird zweimal gelesen und dekomprimiert. Bei bz2 ist die Dekompression der Engpass.
- Der Speicherbedarf bleibt auf wenige Chunks pro Thread begrenzt.
