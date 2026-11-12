# SPEC
## Ziel
CLI-Tool: liest wikidata json file und nutzt den Neo4j batch importer auf Java-Ebene um eine Neo4j Datenbank zu erzeugen.
## Stack
Java 21, Maven, picocli, Neo4j, JUnit, AssertJ
## Formatdetails
JSON Array, jeder Record ist auf einer eigener Zeile, details https://doc.wikimedia.org/Wikibase/master/php/docs_topics_json.html
## Nicht-Ziele
Kein GUI
## Aufruf
Shellscript, das intern `java -jar tools.jar <json-file> <pfad>` aufruft.
## Akzeptanz
Der Batchimporter bricht nicht ab, das erzeugte Verzeichnis ist eine valide Neo4j Datenbank, die nicht leer ist.