# Plan: Gruppen mit Einladungen

Ziel: Fremde können sich registrieren, eine eigene Bierpong-Gruppe anlegen und per Link weitere Leute einladen.
Matches, Spieler und Statistiken gehören jeweils genau einer Gruppe und sind nur für deren Mitglieder sichtbar.

## 1. Entscheidungen (bereits getroffen)

| Thema | Entscheidung |
|---|---|
| Datenbank | Neustart ohne Altdaten, keine Migration |
| Gruppen pro User | mehrere, mit Gruppen-Wechsler |
| Einladungen | Link mit Token (kein Account-Suchen) |
| Wer darf Matches eintragen | zunächst jedes Mitglied; Admin-Only/Bestätigung später |
| Platzhalter-Spieler | später, Modell wird dafür vorbereitet |

Zusätzlich in diesem Plan angenommen (bitte kurz bestätigen oder korrigieren):

- **Gruppen sind privat.** Nur Mitglieder sehen Spieler, Matches und Leaderboard. Ein öffentlicher Lese-Link wäre ein späteres Extra.
- **`Player` bleibt die Entität "Spieler in einer Gruppe".** Sie behält Name und Stats und bekommt zusätzlich `group` und `user`. Der Login-Account wird eine neue Entität `AppUser`. So bleiben `Match`, `MatchService`-Statistiklogik, `PlayerResponse`, `MatchResponse` und die Frontend-Modelle fast unverändert.
- **Login per Username**, global eindeutig. Der Anzeigename (`Player.name`) ist pro Gruppe eindeutig und kann vom Username abweichen. E-Mail und Passwort-Reset kommen später (siehe Abschnitt 9).

## 2. Datenmodell

Postgres-reservierte Wörter vermeiden: Tabellen `app_user` und `pong_group`.

```
app_user                         pong_group
  id            PK                 id          PK
  username      unique (lower)     name
  password      (BCrypt)           created_at
  created_at

player  (= Mitgliedschaft, bisherige Tabelle)
  id          PK
  group_id    FK -> pong_group, NOT NULL
  user_id     FK -> app_user, NULLABLE, ON DELETE SET NULL
              (null = Platzhalter / ehemaliges Mitglied / Account gelöscht)
  name        Anzeigename, unique je (group_id, lower(name))
  role        ADMIN | MEMBER
  wins, losses, win_rate, avg_cups   (unverändert)
  created_at
  unique (group_id, user_id) WHERE user_id IS NOT NULL

match
  group_id    FK -> pong_group, NOT NULL   (neu)
  winner1_id, winner2_id, loser1_id, loser2_id -> player   (unverändert)
  winner1_cups ... played_at                               (unverändert)
  index (group_id, played_at DESC)

invitation
  id, group_id FK, token_hash (SHA-256, unique), created_by -> player,
  created_at, expires_at, max_uses (nullable), uses, revoked_at (nullable)
```

Hinweise:

- `user_id` nullable ist der Haken für spätere Platzhalter-Spieler. Er wird sofort auch gebraucht: Wer eine Gruppe verlässt oder seinen Account löscht, behält seine Matches in der Gruppe als "ehemaliges Mitglied" (`user_id = null`), weil Matches per Fremdschlüssel am Spieler hängen.
- Invariante: Ein `Match` referenziert nur `Player` derselben Gruppe. Das prüft `MatchService` beim Anlegen (alle Spieler kommen aus einer Gruppen-Suche) und ein Test.
- **Alles liegt global in denselben Tabellen** (eine Zeile pro Spieler/Match, getrennt über `group_id`), keine Tabelle pro Gruppe. Die Trennung erzwingen Code und Tests (Abschnitt 3).
- **Indizes:** Postgres legt auf Fremdschlüssel-Spalten nicht automatisch Indizes an, sie müssen explizit angelegt werden. Nötig sind:
  - `match (group_id, played_at DESC)` für "letzte Matches einer Gruppe",
  - `player (group_id, lower(name))` (der Unique-Index, `group_id` steht vorn und deckt damit alle Spieler-Abfragen einer Gruppe ab),
  - `invitation (group_id)` und `invitation (token_hash)` (unique),
  - `match (winner1_id)`, `(winner2_id)`, `(loser1_id)`, `(loser2_id)`, sobald "Matches eines Spielers" angezeigt werden. Spätestens beim Löschen/Austreten ist es nötig, weil Postgres sonst per Scan prüft, ob noch Matches auf den Spieler zeigen.
  Mit diesen Indizes hängt die Abfragedauer von der Größe der einen Gruppe ab, nicht von der Gesamtzahl der Gruppen.
- `GET .../matches` liefert aktuell alle Matches einer Gruppe. Bei langen Historien später auf Seitenaufteilung umstellen (siehe Abschnitt 9).
- `schema-postgresql.sql` ersetzt den alten Index `ux_player_name_lower` durch die neuen Unique-Indizes (`lower(username)`, `(group_id, lower(name))`, partieller Index auf `(group_id, user_id)`).
- **Flyway (umgesetzt in Etappe 1):** Das Schema kommt aus versionierten Migrationen unter `backend/src/main/resources/db/migration/` (`V1__init.sql`, jede Etappe mit Schemaänderung bekommt eine weitere Datei). Hibernate läuft mit `ddl-auto=validate` und prüft nur. Eine einmal ausgelieferte Migration wird nie mehr geändert, sondern durch eine neue ergänzt. Die Tests nutzen Mocks und berühren die DB nicht, die Eindeutigkeit wird zusätzlich im Service geprüft.

Umsetzung in Etappe 2: Die Entität heißt `PongGroup` (`Group` ist in HQL/SQL ein Schlüsselwort), `created_by` entfällt. Das Schema steht in `V2__groups.sql`.

## 3. Rollen und Berechtigungen

| Aktion | Nicht eingeloggt | Eingeloggt, kein Mitglied | MEMBER | ADMIN |
|---|---|---|---|---|
| Registrieren, Login | ja | - | - | - |
| Gruppe anlegen | nein | ja (wird ADMIN) | - | - |
| Einladungs-Vorschau (Gruppenname) | ja, mit Token | ja, mit Token | - | - |
| Einladung annehmen | nein | ja | - | - |
| Spieler, Matches, Leaderboard lesen | nein | nein (404) | ja | ja |
| Match eintragen | nein | nein | ja | ja |
| Gruppe verlassen | - | - | ja | ja (nicht als letzter Admin) |
| Einladungen erstellen/widerrufen | nein | nein | nein | ja |
| Mitglied entfernen, Rolle ändern | nein | nein | nein | ja |
| Gruppe umbenennen/löschen | nein | nein | nein | ja |

Regeln:

- **Nicht-Mitglieder bekommen 404 statt 403**, damit man Gruppen-IDs nicht durchprobieren kann.
- **Letzter Admin:** Er kann die Gruppe nicht verlassen und sich nicht herabstufen. Er muss erst jemanden befördern oder die Gruppe löschen. Gleiches gilt für das Löschen des Accounts.
- **Default wird umgedreht:** In `SecurityConfig` steht aktuell `anyRequest().permitAll()`. Neu: `/api/**` ist authentifiziert, Ausnahmen sind nur `POST /api/auth/login`, `POST /api/auth/register` und `GET /api/invitations/{token}`. Statische Frontend-Ressourcen bleiben offen.
- **Zentrale Prüfung statt verstreuter Checks (umgesetzt in Etappe 2):** Ein `HandlerInterceptor` (`GroupAccessInterceptor`) hängt am Pfad `/api/groups/{groupId}/**` und ruft `GroupAccess.requireMember(groupId, userId)` auf. Ein neuer Endpoint unter diesem Pfad ist damit automatisch geschützt, und Nicht-Mitglieder bekommen 404 (statt 403, wie es `@PreAuthorize` liefern würde). `requireAdmin` kommt mit den ersten Admin-Endpoints in Etappe 3. Zusätzlich nehmen alle Repository-Methoden die `groupId` als Parameter (Defense in Depth: auch wenn ein Check fehlt, liefert die Query nichts aus fremden Gruppen).

## 4. API

Alle bisherigen Daten-Endpoints wandern unter `/api/groups/{groupId}/...`. Antwort-DTOs (`PlayerResponse`, `MatchResponse`) bleiben gleich.

| Methode + Pfad | Zweck | Zugriff |
|---|---|---|
| `POST /api/auth/register` `{username, password}` | Account anlegen, ersetzt `POST /api/players` | offen |
| `POST /api/auth/login` `{username, password}` | Login | offen |
| `POST /api/auth/logout` | Logout | eingeloggt |
| `GET /api/auth/me` | `{id, username}` | eingeloggt |
| `DELETE /api/auth/me` | Account löschen (Spieler werden "ehemalig") | eingeloggt |
| `GET /api/groups` | meine Gruppen: `{id, name, role, playerId, wins, losses, winRate, avgCups, memberCount}` | eingeloggt |
| `POST /api/groups` `{name, displayName?}` | Gruppe anlegen, Ersteller wird ADMIN | eingeloggt |
| `GET /api/groups/{gid}` | Gruppendetails | Mitglied |
| `PATCH /api/groups/{gid}` `{name}` | umbenennen | Admin |
| `DELETE /api/groups/{gid}` | Gruppe samt Matches/Spielern/Einladungen löschen (eine Transaktion) | Admin |
| `GET /api/groups/{gid}/leaderboard` | wie bisher, gruppenbezogen | Mitglied |
| `GET /api/groups/{gid}/players?sort=` und `/players/search?name=` und `/players/{id}` | wie bisher | Mitglied |
| `GET /api/groups/{gid}/matches`, `/matches/recent`, `/matches/{id}` | wie bisher, `{id}` muss zur Gruppe gehören | Mitglied |
| `POST /api/groups/{gid}/matches` | Match eintragen (Namen werden in der Gruppe aufgelöst) | Mitglied |
| `GET /api/groups/{gid}/members` | Mitglieder mit Rolle | Mitglied |
| `PATCH /api/groups/{gid}/members/{playerId}` `{role}` | befördern/herabstufen | Admin |
| `DELETE /api/groups/{gid}/members/{playerId}` | Mitglied entfernen (wird "ehemalig") | Admin |
| `DELETE /api/groups/{gid}/members/me` | Gruppe verlassen | Mitglied |
| `POST /api/groups/{gid}/invitations` `{expiresInDays?, maxUses?}` | Link erzeugen, Token wird nur einmal im Klartext ausgegeben | Admin |
| `GET /api/groups/{gid}/invitations` / `DELETE .../{id}` | auflisten / widerrufen | Admin |
| `GET /api/invitations/{token}` | Vorschau: Gruppenname, Mitgliederzahl | offen |
| `POST /api/invitations/{token}/accept` `{displayName?}` | beitreten, Default-Name = Username | eingeloggt |

Fehlerfälle für `accept`: Token unbekannt/abgelaufen/widerrufen/aufgebraucht, jeweils die gleiche neutrale Antwort (410 Gone). Bereits Mitglied: 409. Anzeigename in der Gruppe vergeben: 409 mit Hinweis, einen anderen zu wählen.

## 5. Einladungs-Token

- 32 Byte aus `SecureRandom`, URL-sicher Base64-kodiert. Link: `https://<host>/join/<token>`.
- In der DB liegt nur der SHA-256-Hash, ein DB-Leak liefert also keine benutzbaren Links.
- Standard: 7 Tage gültig, optional `maxUses`. Beim Annehmen wird `uses` atomar hochgezählt (`UPDATE ... WHERE uses < max_uses AND expires_at > now() AND revoked_at IS NULL`), damit zwei gleichzeitige Annahmen das Limit nicht überschreiten.
- Wer nicht eingeloggt ist, wird von `/join/:token` zu Login/Registrierung geschickt und danach zurückgeleitet.

## 6. Backend-Umbau (Dateien)

Alle Pfade unter `backend/src/main/java/de/niklasvoelker/beerpongstats/`.

| Bereich | Änderung |
|---|---|
| `model/` | neu: `AppUser`, `Group` (Tabelle `pong_group`), `Invitation`, `Role`. `Player` bekommt `group`, `user`, `role`. `Match` bekommt `group`. |
| `repository/` | neu: `AppUserRepository`, `GroupRepository`, `InvitationRepository`. `PlayerRepository` und `MatchRepository`: alle Methoden nehmen `groupId` (`findByGroupIdAndNameIgnoreCase`, `findTopPlayersByWinrate(groupId, ...)`, `findTop10ByGroupIdOrderByPlayedAtDesc`, ...). Die Duplikat-Prüfung beim Match bekommt `group` dazu. |
| `service/` | `PlayerService` wird gruppenbezogen (Abfragen). Neu: `UserService` (register, Account löschen), `GroupService` (anlegen, umbenennen, löschen, verlassen, Rollen), `InvitationService`, `GroupAccess`. `MatchService` bekommt `groupId` und löst Namen nur innerhalb der Gruppe auf. |
| `api/` | `AuthApiController` (register, login, me, delete me). `PlayerApiController`/`MatchApiController` unter `/api/groups/{groupId}`. Neu: `GroupApiController`, `InvitationApiController`. |
| `api/dto/` | neu: `RegisterRequest`, `CreateGroupRequest`, `GroupResponse`, `MemberResponse`, `InvitationResponse`. `LoginRequest.name` wird `username`. |
| `config/` | `SecurityConfig`: Default `authenticated`, Method Security an, `UserDetailsService` auf `AppUserRepository`. `PlayerUserDetails` wird `AppUserDetails` (id = User-ID). |
| `api/ApiExceptionHandler` | neue Exceptions: Gruppe/Mitgliedschaft nicht gefunden (404), keine Admin-Rechte (403), Einladung ungültig (410), letzter Admin (409). Die Meldung bei Login-Fehler wird "Benutzername oder Passwort falsch". |
| `resources/schema-postgresql.sql` | neue Indizes (Abschnitt 2). |

Entscheidungen zu den Randfällen:

- **Account löschen:** Alle `Player` des Users werden `user_id = null` (Matches und Stats der Gruppen bleiben erhalten). Der Anzeigename bleibt als Gruppen-Spitzname stehen. Wenn der User letzter Admin einer Gruppe mit weiteren Mitgliedern ist, wird das Löschen mit einer klaren Meldung abgelehnt. Ist er das einzige Mitglied, wird die Gruppe mitgelöscht.
- **Gruppe verlassen/Mitglied entfernen:** Ebenfalls `user_id = null`, Name bleibt, damit bestehende Matches lesbar bleiben.
- **Limits gegen Missbrauch:** z. B. max. 20 Gruppen pro User und max. 100 Mitglieder pro Gruppe, konfigurierbar in `application.properties`.

## 7. Frontend-Umbau

Routing mit einer komponentenlosen Eltern-Route, damit `groupId` per `withComponentInputBinding` in die Seiten kommt:

```
/                      Landing (ausgeloggt) bzw. Redirect auf die letzte/einzige Gruppe, sonst /groups
/login, /register      Login, Registrierung (ersetzt /newPlayer)
/groups                Gruppenliste + "Gruppe erstellen"
/join/:token           Einladung ansehen und annehmen
/g/:groupId            Home (Leaderboard + letzte Matches)      } groupGuard:
/g/:groupId/players, /players/:id                                } eingeloggt und Mitglied,
/g/:groupId/matches, /matches/:id, /newMatch                     } sonst Redirect auf /groups
/g/:groupId/settings   Mitglieder, Einladungslinks, Umbenennen, Löschen (Admin-Teile nur für Admins)
/profile               Account, Stats pro Gruppe, Account löschen
```

| Datei | Änderung |
|---|---|
| `api/models.ts` | neu: `Me`, `GroupSummary`, `Member`, `Invitation`. `Player`/`Match` bleiben. |
| `api/auth-api.ts` | `currentUser: Me`, `register()`, `login(username, password)`, `deleteAccount()`. |
| `api/player-api.ts`, `match-api.ts` | alle Methoden nehmen `groupId`: `/api/groups/${groupId}/...`. Bestehende Tests in `api.spec.ts` entsprechend anpassen. |
| neu `api/group-api.ts` | Gruppen, Mitglieder, Einladungen. |
| neu `group-guard.ts`, Anpassung `auth-guard.ts` | Login-Pflicht auf allen Seiten außer Landing/Login/Register/`/join`. Beim Ablehnen merkt sich der Guard die Ziel-URL für den Redirect nach dem Login. |
| `app.html` / `app.ts` | Navbar zeigt Gruppenname mit Wechsler-Dropdown. "New Player" entfällt, Links bekommen das `/g/:groupId`-Präfix. |
| `pages/forms/new-player.*` | wird `pages/auth/register.*`. |
| `pages/forms/new-match.*` | Spielernamen als Auswahl (`<datalist>` mit den Gruppenmitgliedern) statt Freitext, weniger Tippfehler. |
| `pages/profile/*` | zeigt pro Gruppe die eigenen Stats, "Account löschen" nutzt `DELETE /api/auth/me`. |
| neu `pages/groups/*`, `pages/join/*`, `pages/group-settings/*` | Gruppenliste/-erstellung, Einladung annehmen, Mitglieder- und Einladungsverwaltung. |
| Tests | `app.spec.ts`, `pages.spec.ts`, `api.spec.ts` mitziehen. |

## 8. Etappen

Jede Etappe endet in einem lauffähigen, getesteten Stand (`./gradlew test`, `npm test`) und kann ein eigener PR sein.

**Etappe 1: Accounts statt Spieler-Login (Backend, klein)**
- `AppUser`, Register/Login/Logout/Me/Delete-Me, Security-Default `authenticated`.
- Noch ohne Gruppen: Der alte Datenbereich bleibt vorübergehend erreichbar, `/api/**` ist aber schon geschützt.
- Fertig, wenn: Registrieren, Einloggen und Session-Wiederherstellung funktionieren, Controller- und Security-Tests grün.

**Etappe 2: Gruppen-Kern und Isolation (Backend, der größte Brocken)**
- `Group`, `Player.group/user/role`, `Match.group`, `GroupAccess`, alle Endpoints unter `/api/groups/{gid}/...`, Gruppe anlegen und meine Gruppen.
- Fertig, wenn: ein Integrationstest mit zwei Gruppen belegt, dass jeder Lese- und Schreib-Endpoint für Nicht-Mitglieder 404 liefert und nie Daten der anderen Gruppe zurückgibt. Match mit Spieler einer fremden Gruppe wird abgelehnt.

**Etappe 3: Einladungen und Mitgliederverwaltung (Backend)**
- `Invitation`, Token-Erzeugung und Hash, Vorschau/Annehmen, Rollen, Verlassen/Entfernen, Gruppe umbenennen/löschen, Account-Löschen-Regeln, Limits.
- Fertig, wenn: Tests für abgelaufen, widerrufen, aufgebraucht, parallele Annahme, doppelter Beitritt, Namenskonflikt, letzter Admin.

**Stand Etappe 3a (umgesetzt):** Einladungen (V3, Hash-Token, atomares Hochzählen), Vorschau und Beitreten, Mitgliederliste, Rollen ändern, Gruppe verlassen und die Regeln beim Account-Löschen. Abweichungen vom Plan:
- `PATCH /members/{playerId}` (Rolle ändern) ist schon in 3a, weil die Regel "letzter Admin" sonst nicht aufzulösen wäre.
- `invitation.created_by` entfällt.
- `memberCount` zählt nur Spieler mit Account. Wer geht, bleibt als Spieler ohne Account in der Gruppe stehen.
- Verlässt das letzte Mitglied die Gruppe oder löscht seinen Account, wird die Gruppe samt Matches, Spielern und Einladungen gelöscht (intern, noch ohne eigenen Endpoint).
- Wer zurückkommt, bekommt einen neuen Spieler und braucht einen freien Namen.

**Stand Etappe 3b (umgesetzt):** `DELETE /members/{playerId}` (Mitglied entfernen), `PATCH /api/groups/{gid}` (umbenennen), `DELETE /api/groups/{gid}` (löschen) und die Limits. Entscheidungen dazu:
- Ein Admin kann andere Admins nicht direkt entfernen und sich selbst nicht über diesen Endpoint. Erst herabstufen, zum Verlassen gibt es `members/me`.
- Wer entfernt wurde, kann mit einem noch gültigen Link wieder beitreten (mit neuem Namen).
- Limits stehen in `application.properties` (`beerpong.limits.max-groups-per-user=20`, `beerpong.limits.max-members-per-group=100`). Das Beitreten zählt gegen beide.
- Beitritte sperren die Gruppenzeile (`SELECT ... FOR UPDATE`), damit parallele Beitritte die Mitgliederobergrenze nicht überschreiten.

**Etappe 4: Frontend (kann nach Etappe 2 beginnen, sobald die API-Verträge stehen)**
- Reihenfolge: Auth-Seiten und Guards, Gruppenliste/-erstellung, Umstellung der bestehenden Seiten auf `/g/:groupId`, Navbar-Wechsler, `/join/:token`, Settings-Seite, Profil.
- Fertig, wenn: kompletter Ablauf im Browser funktioniert: registrieren, Gruppe anlegen, Link kopieren, mit zweitem Account beitreten, Match eintragen, Leaderboard sehen.

**Etappe 5: Absicherung und Rollout**
- Rate Limiting auf Login, Registrierung und `accept` (z. B. Bucket4j oder ein einfacher IP-basierter Filter), Passwort-Mindestlänge von 4 auf 8, Username-Regeln (Länge, erlaubte Zeichen).
- Impressum und Datenschutzerklärung als statische Seiten, README und Docker-Hinweise aktualisieren.
- Produktions-DB leeren (Render: Schema neu anlegen bzw. Datenbank neu erstellen), deployen, Smoke-Test mit zwei Accounts.

## 9. Bewusst nicht in diesem Plan (spätere Etappen)

- **Platzhalter-Spieler** für Freunde ohne Account: Admin legt `Player` mit `user_id = null` an, ein Einladungslink "übernimmt" ihn später. Das Modell trägt das bereits.
- **Wer darf Matches eintragen:** Admin-Only oder Bestätigung durch ein Gegner-Mitglied.
- **E-Mail und Passwort-Reset.** Ohne Reset sind vergessene Passwörter bei öffentlichen Nutzern ein Supportfall, daher vor einem größeren Start einplanen.
- **Öffentlicher Lese-Link** für eine Gruppe, Admin-Übergabe per Klick, Einladung direkt an einen bestehenden Account.
- **Seitenaufteilung** für `GET .../matches` (Pageable, "mehr laden" im Frontend), sobald Gruppen lange Historien haben.
- **Stats-Robustheit:** `wins`, `losses`, `avgCups` werden beim Eintragen auf der Zeile hochgezählt. Bei vielen gleichzeitigen Einträgen in einer Gruppe können Updates verloren gehen. Lösung: `@Version` (Optimistic Locking) oder Stats aus den Matches ableiten.

## 10. Risiken

- **Datenleck zwischen Gruppen** ist das größte Risiko. Gegenmaßnahmen: Default `authenticated`, zentrales `GroupAccess`, `groupId` in jeder Repository-Methode, der Isolationstest aus Etappe 2.
- **Größe von Etappe 2:** Sie berührt fast jede Backend-Datei. Deshalb getrennt von Einladungen gehalten.
- **Session-Verhalten nach Account-Löschung:** Die bestehende Logik (Session endet, CSRF-Cookie wird entfernt) muss für `DELETE /api/auth/me` übernommen werden, siehe `PlayerApiController.deletePlayer` und `profile.ts`.
