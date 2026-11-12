# Projekt
Siehe @SPEC.md für Anforderungen.

## Befehle
- Build+Tests: `./mvnw -q verify`
- Einzeltest: `./mvnw -q test -Dtest=HeaderParserTest`

## Regeln
- Nach jeder Änderung `./mvnw -q verify` ausführen, erst bei grün fertig melden
- Keine neuen Dependencies ohne Rückfrage
- Records statt Lombok, keine Field-Injection
- Tests mit AssertJ, Fixtures aus src/test/resources