# 1. Neo4j 5 mit internem BatchImporter

Status: akzeptiert

## Kontext
Die Datenbank soll auf Java-Ebene mit dem Neo4j-Batchimporter erzeugt werden. Die alte `BatchInserter`-API existiert ab Neo4j 5 nicht mehr.

## Entscheidung
Neo4j 5.26 (LTS, Community) mit dem internen `org.neo4j.internal.batchimport.BatchImporter`, erzeugt über die `BatchImporterFactory`. Knoten-Ids sind Strings (`IdType.STRING`), also die Wikidata-Ids bzw. Statement-Ids.

## Konsequenzen
- Die API ist intern und kann sich zwischen Neo4j-Versionen ändern. Die Neo4j-Version ist deshalb im `pom.xml` fest gepinnt.
- Neo4j 5.26 ist für Java 21 ausgelegt, daher `release 21`.
- Ein Spike-Test sichert den Vertrag des Importers ab (je ein Durchlauf über Knoten und Kanten, alle Knoten vor der ersten Kante).
