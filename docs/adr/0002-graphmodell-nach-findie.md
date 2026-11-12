# 2. Graphmodell nach findie/wikidata-neo4j-importer

Status: akzeptiert

## Kontext
Das Modell des Wikidata-Graphen in Neo4j muss festgelegt werden.

## Entscheidung
Wir übernehmen das Modell von https://github.com/findie/wikidata-neo4j-importer:

- `:Entity:Item` und `:Entity:Property` mit `id`, `label`, `slug`, `descriptions[]`, `aliases[]` (nur Englisch). Properties tragen zusätzlich `datatype`.
- Claims vom Typ `wikibase-item` und `wikibase-property` werden zu Kanten `(Entity)-[:<PROPERTY_LABEL> {by, id}]->(Entity)`. Der Kantentyp ist das Label der Property in Großbuchstaben, Fallback ist `CLAIM`.
- Alle anderen Datentypen werden zu `:Claim:<Datentyp>`-Knoten, angehängt über `(Entity)-[:<PROPERTY_LABEL> {by}]->(Claim)`.
- `:Quantity` und `:GlobeCoordinate` bekommen `UNIT_TYPE` bzw. `GLOBE_TYPE` auf die Ziel-Entity. Die Felder `unit` und `globe` entfallen dabei.

Nicht übernommen werden Qualifiers, References, Sitelinks, Lexemes und Snaks mit `snaktype` ungleich `value`.

## Konsequenzen
- Die Statement-Id dient als Id des Claim-Knotens, damit die Kanten die Knoten wiederfinden.
- Der Graph bleibt deutlich kleiner als bei einem vollständigen Modell.
