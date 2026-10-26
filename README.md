# BeerpongStats

Statistik-App für Beerpong: Spieler, Matches und ein Leaderboard.

- **Backend:** Spring Boot 4 (Java 21), PostgreSQL, REST-API unter `/api`
- **Frontend:** Angular (`frontend/`), wird in Produktion vom Backend mit ausgeliefert

## Produktion / alles in Docker

```bash
docker compose up --build
```

Das Docker-Image baut zuerst das Frontend (Node), kopiert es nach `static/` und baut dann das Backend.
Danach ist alles unter http://localhost:8080 erreichbar.

## Entwicklung

Backend (braucht eine laufende Postgres-DB, z. B. `docker compose up -d db`):

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/beerpongdb \
SPRING_DATASOURCE_USERNAME=beerpong \
SPRING_DATASOURCE_PASSWORD=beerpong123 \
SPRING_JPA_HIBERNATE_DDL_AUTO=update \
./gradlew bootRun
```

Frontend (Dev-Server mit Proxy von `/api` auf `localhost:8080`):

```bash
cd frontend
npm install
npm start
```

Dann http://localhost:4200 öffnen.

## Tests

```bash
./gradlew test
cd frontend && npm test
```
