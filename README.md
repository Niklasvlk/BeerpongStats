# BeerpongStats

Statistik-App für Beerpong: Spieler, Matches und ein Leaderboard.

- **Backend:** Spring Boot 4 (Java 21), PostgreSQL, REST-API unter `/api` (`backend/`)
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
cd backend
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/beerpongdb \
SPRING_DATASOURCE_USERNAME=beerpong \
SPRING_DATASOURCE_PASSWORD=beerpong123 \
./gradlew bootRun
```

Frontend (Dev-Server mit Proxy von `/api` auf `localhost:8080`):

```bash
cd frontend
npm install
npm start
```

Dann http://localhost:4200 öffnen.

## Konfiguration

Die Werte stehen in `backend/src/main/resources/application.properties` und lassen sich wie jede Spring-Property über
Umgebungsvariablen überschreiben (z. B. `BEERPONG_RATE_LIMIT_LOGIN_PER_MINUTE=20`).

| Property | Standard | Bedeutung |
|---|---|---|
| `beerpong.limits.max-groups-per-user` | 20 | Gruppen, in denen ein Account Mitglied sein kann |
| `beerpong.limits.max-members-per-group` | 100 | Mitglieder (Spieler mit Account) einer Gruppe |
| `beerpong.rate-limit.enabled` | true | Rate Limiting an oder aus |
| `beerpong.rate-limit.login-per-minute` | 10 | Login-Versuche pro Minute und Client-Adresse |
| `beerpong.rate-limit.register-per-hour` | 10 | Registrierungen pro Stunde und Client-Adresse |
| `beerpong.rate-limit.join-per-minute` | 20 | Beitritte über Einladungslinks pro Minute und Client-Adresse |
| `beerpong.rate-limit.failed-logins-per-username` | 10 | Falsche Passwörter pro 15 Minuten und Benutzername, von jeder Adresse |

Das Rate Limiting zählt im Speicher: Es gilt nur für die laufende Instanz und beginnt nach einem Neustart von vorn.
Hinter einem Proxy (z. B. Render) zählt es nach der Adresse aus `X-Forwarded-For`; dafür muss
`server.forward-headers-strategy=native` gesetzt sein (ist es). Passwörter müssen mindestens 8 Zeichen lang sein
(höchstens 72 Bytes, mehr verarbeitet BCrypt nicht).

## Tests

```bash
cd backend && ./gradlew test
cd frontend && npm test
```
