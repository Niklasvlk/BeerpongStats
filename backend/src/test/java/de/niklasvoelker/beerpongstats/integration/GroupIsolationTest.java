package de.niklasvoelker.beerpongstats.integration;

import com.jayway.jsonpath.JsonPath;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.model.Role;
import de.niklasvoelker.beerpongstats.repository.AppUserRepository;
import de.niklasvoelker.beerpongstats.repository.GroupRepository;
import de.niklasvoelker.beerpongstats.repository.MatchRepository;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Beweist, dass eine Gruppe nie Daten einer anderen sieht. Alle Gruppen liegen in denselben Tabellen und werden nur
 * über group_id getrennt; ein vergessener Filter oder eine vergessene Prüfung fällt hier auf.
 *
 * <p>Aufbau: Alice ist Admin der Gruppe A (mit Anna und Max), Bob Admin der Gruppe B (mit Ben und ebenfalls einem Max).
 * In jeder Gruppe wurden 10 Matches gespielt. Carol ist in keiner Gruppe.
 */
class GroupIsolationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "geheim1234";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private MatchRepository matchRepository;

    private MockHttpSession alice;
    private MockHttpSession bob;
    private MockHttpSession carol;
    private long groupA;
    private long groupB;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE match, player, pong_group, app_user RESTART IDENTITY CASCADE");

        register("Alice");
        register("Bob");
        register("Carol");
        alice = login("Alice");
        bob = login("Bob");
        carol = login("Carol");

        groupA = createGroup(alice, "Gruppe A", null);
        groupB = createGroup(bob, "Gruppe B", null);

        addPlaceholder(groupA, "Anna");
        addPlaceholder(groupA, "Max");
        addPlaceholder(groupB, "Ben");
        addPlaceholder(groupB, "Max");

        for (int cups : IntStream.range(0, 10).toArray()) {
            playMatch(alice, groupA, "Alice", "Anna", cups);
            playMatch(bob, groupB, "Bob", "Ben", cups);
        }
    }

    // --- Hilfsmethoden ---

    private static String json(String... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            sb.append(i > 0 ? "," : "").append('"').append(keyValues[i]).append("\":\"").append(keyValues[i + 1]).append('"');
        }
        return sb.append('}').toString();
    }

    private void register(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", username, "password", PASSWORD)))
                .andExpect(status().isCreated());
    }

    // Echter Login: die Session trägt den Security-Kontext, wie im Browser
    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", username, "password", PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long createGroup(MockHttpSession session, String name, String displayName) throws Exception {
        String body = displayName == null ? json("name", name) : json("name", name, "displayName", displayName);
        MvcResult result = mockMvc.perform(post("/api/groups").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    // Ein Spieler ohne Account, so wie später Platzhalter oder ehemalige Mitglieder aussehen
    private void addPlaceholder(long groupId, String name) {
        playerRepository.save(Player.builder()
                .group(groupRepository.getReferenceById(groupId))
                .name(name)
                .role(Role.MEMBER)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void playMatch(MockHttpSession session, long groupId, String winner, String loser, int loserCups) throws Exception {
        mockMvc.perform(post("/api/groups/" + groupId + "/matches").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winner1\":\"" + winner + "\",\"loser1\":\"" + loser
                                + "\",\"winner1Cups\":10,\"loser1Cups\":" + loserCups + "}"))
                .andExpect(status().isCreated());
    }

    private ResultActions getAs(MockHttpSession session, String url) throws Exception {
        return mockMvc.perform(get(url).session(session));
    }

    private long idFromList(MockHttpSession session, String url, int index) throws Exception {
        String body = getAs(session, url).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$[" + index + "].id")).longValue();
    }

    // --- Mitglieder sehen nur ihre eigene Gruppe ---

    @Test
    void aMemberSeesOnlyTheDataOfTheirOwnGroup() throws Exception {
        String a = "/api/groups/" + groupA;

        getAs(alice, a + "/players").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Alice", "Anna", "Max")));
        getAs(alice, a + "/players?sort=winrate").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Alice", "Anna", "Max")));
        getAs(alice, a + "/players?sort=avgCups").andExpect(jsonPath("$", hasSize(3)));
        getAs(alice, a + "/players?sort=name").andExpect(jsonPath("$", hasSize(3)));
        getAs(alice, a + "/players/search?name=b").andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
        getAs(alice, a + "/players/search?name=a").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Alice", "Anna", "Max")));
        getAs(alice, a + "/leaderboard").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Alice", "Anna")));
        getAs(alice, a + "/matches").andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[*].winner1.name").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("Alice"))))
                .andExpect(jsonPath("$[*].loser1.name").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("Anna"))));
        getAs(alice, a + "/matches/recent").andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)));
    }

    @Test
    void myGroupsListsOnlyTheGroupsOfTheSignedInUser() throws Exception {
        getAs(alice, "/api/groups").andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(groupA))
                .andExpect(jsonPath("$[0].name").value("Gruppe A"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].wins").value(10))
                .andExpect(jsonPath("$[0].memberCount").value(3));
        getAs(bob, "/api/groups").andExpect(jsonPath("$[0].id").value(groupB));
        getAs(carol, "/api/groups").andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
    }

    // --- Nicht-Mitglieder kommen nirgends durch ---

    @Test
    void aNonMemberGets404OnEveryEndpointOfAnotherGroup() throws Exception {
        long annaId = idFromList(alice, "/api/groups/" + groupA + "/players?sort=name", 1);
        long matchId = idFromList(alice, "/api/groups/" + groupA + "/matches", 0);
        String a = "/api/groups/" + groupA;

        for (MockHttpSession outsider : List.of(bob, carol)) {
            getAs(outsider, a).andExpect(status().isNotFound());
            getAs(outsider, a + "/leaderboard").andExpect(status().isNotFound());
            getAs(outsider, a + "/players").andExpect(status().isNotFound());
            getAs(outsider, a + "/players?sort=winrate").andExpect(status().isNotFound());
            getAs(outsider, a + "/players/search?name=a").andExpect(status().isNotFound());
            getAs(outsider, a + "/players/" + annaId).andExpect(status().isNotFound());
            getAs(outsider, a + "/matches").andExpect(status().isNotFound());
            getAs(outsider, a + "/matches/recent").andExpect(status().isNotFound());
            getAs(outsider, a + "/matches/" + matchId).andExpect(status().isNotFound());
            mockMvc.perform(post(a + "/matches").with(csrf()).session(outsider)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"winner1\":\"Alice\",\"loser1\":\"Anna\",\"winner1Cups\":10,\"loser1Cups\":3}"))
                    .andExpect(status().isNotFound());
        }

        assertEquals(10, matchRepository.findAllByGroupIdOrderByPlayedAtDesc(groupA).size());
    }

    @Test
    void anAnswerForAMissingGroupLooksTheSameAsForAForeignOne() throws Exception {
        MvcResult foreign = getAs(bob, "/api/groups/" + groupA + "/players").andExpect(status().isNotFound()).andReturn();
        MvcResult missing = getAs(bob, "/api/groups/99999/players").andExpect(status().isNotFound()).andReturn();
        getAs(bob, "/api/groups/abc/players").andExpect(status().isNotFound());

        assertEquals(JsonPath.<String>read(foreign.getResponse().getContentAsString(), "$.detail"),
                JsonPath.<String>read(missing.getResponse().getContentAsString(), "$.detail"));
    }

    @Test
    void anonymousRequestsGet401() throws Exception {
        mockMvc.perform(get("/api/groups")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/groups/" + groupA)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/groups/" + groupA + "/players")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/groups/" + groupA + "/matches")).andExpect(status().isUnauthorized());
    }

    // --- IDs aus einer anderen Gruppe sind über die eigene Gruppe nicht erreichbar ---

    @Test
    void anIdOfAnotherGroupIsNotReachableThroughYourOwnGroup() throws Exception {
        long aliceId = idFromList(alice, "/api/groups/" + groupA + "/players?sort=name", 0);
        long matchOfA = idFromList(alice, "/api/groups/" + groupA + "/matches", 0);
        String b = "/api/groups/" + groupB;

        getAs(bob, b + "/players/" + aliceId).andExpect(status().isNotFound());
        getAs(bob, b + "/matches/" + matchOfA).andExpect(status().isNotFound());

        // Gegenprobe: die eigenen IDs funktionieren
        long benId = idFromList(bob, b + "/players?sort=name", 0);
        getAs(bob, b + "/players/" + benId).andExpect(status().isOk());
    }

    @Test
    void aMatchCannotUsePlayersOfAnotherGroup() throws Exception {
        // "Alice" gibt es nur in Gruppe A
        mockMvc.perform(post("/api/groups/" + groupB + "/matches").with(csrf()).session(bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winner1\":\"Alice\",\"loser1\":\"Bob\",\"winner1Cups\":10,\"loser1Cups\":3}"))
                .andExpect(status().isNotFound());

        assertEquals(10, matchRepository.findAllByGroupIdOrderByPlayedAtDesc(groupB).size());
        getAs(alice, "/api/groups/" + groupA + "/players?sort=name")
                .andExpect(jsonPath("$[0].name").value("Alice"))
                .andExpect(jsonPath("$[0].wins").value(10))
                .andExpect(jsonPath("$[0].losses").value(0));
    }

    @Test
    void aMatchBelongsToTheGroupItWasPlayedIn() {
        assertEquals(10, matchRepository.findAllByGroupIdOrderByPlayedAtDesc(groupA).size());
        assertEquals(10, matchRepository.findAllByGroupIdOrderByPlayedAtDesc(groupB).size());
        assertEquals(10, jdbc.queryForObject("""
                SELECT count(*) FROM match m JOIN player p ON p.id = m.winner1_id
                WHERE m.group_id = p.group_id AND m.group_id = ?
                """, Integer.class, groupA));
    }

    // --- Gleiche Namen in verschiedenen Gruppen ---

    @Test
    void theSameDisplayNameInTwoGroupsIsKeptApart() throws Exception {
        playMatch(alice, groupA, "Max", "Anna", 4);

        getAs(alice, "/api/groups/" + groupA + "/players?sort=name")
                .andExpect(jsonPath("$[2].name").value("Max"))
                .andExpect(jsonPath("$[2].wins").value(1));
        getAs(bob, "/api/groups/" + groupB + "/players?sort=name")
                .andExpect(jsonPath("$[2].name").value("Max"))
                .andExpect(jsonPath("$[2].wins").value(0));
    }

    @Test
    void displayNamesAreUniqueWithinAGroupIgnoringCase() {
        assertThrows(DataIntegrityViolationException.class, () -> addPlaceholder(groupA, "max"));
    }

    // --- Mehrere Gruppen pro Account ---

    @Test
    void anAccountCanBeInSeveralGroupsWithSeparateNamesAndStats() throws Exception {
        long groupC = createGroup(alice, "Gruppe C", "Ali");

        getAs(alice, "/api/groups").andExpect(jsonPath("$", hasSize(2)));
        getAs(alice, "/api/groups/" + groupC + "/players")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Ali"))
                .andExpect(jsonPath("$[0].wins").value(0));
        // In Gruppe A bleibt sie "Alice" mit ihren Siegen
        getAs(alice, "/api/groups/" + groupA + "/players?sort=name")
                .andExpect(jsonPath("$[0].name").value("Alice"))
                .andExpect(jsonPath("$[0].wins").value(10));
        // Bob sieht Gruppe C nicht
        getAs(bob, "/api/groups/" + groupC).andExpect(status().isNotFound());
    }

    @Test
    void anAccountIsMemberOfAGroupAtMostOnce() {
        Player second = Player.builder()
                .group(groupRepository.getReferenceById(groupA))
                .user(userRepository.findByUsernameIgnoreCase("Alice").orElseThrow())
                .name("Alice zwei")
                .role(Role.MEMBER)
                .createdAt(LocalDateTime.now())
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> playerRepository.save(second));
    }

    // --- Gruppe anlegen ---

    @Test
    void theCreatorOfAGroupIsAdminAndNamedLikeTheirUsernameByDefault() throws Exception {
        long groupC = createGroup(carol, "Gruppe C", null);

        getAs(carol, "/api/groups/" + groupC).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.memberCount").value(1));
        getAs(carol, "/api/groups/" + groupC + "/players")
                .andExpect(jsonPath("$[0].name").value("Carol"));
    }

    @Test
    void aGroupNeedsAName() throws Exception {
        mockMvc.perform(post("/api/groups").with(csrf()).session(carol)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("name", "  ")))
                .andExpect(status().isBadRequest());

        getAs(carol, "/api/groups").andExpect(jsonPath("$", empty()));
    }

    // --- Account löschen ---

    @Test
    void deletingTheAccountKeepsThePlayersAndMatchesOfItsGroups() throws Exception {
        mockMvc.perform(delete("/api/auth/me").with(csrf()).session(alice)).andExpect(status().isNoContent());

        List<Player> players = playerRepository.findByGroupId(groupA, Sort.by("name"));
        assertEquals(List.of("Alice", "Anna", "Max"), players.stream().map(Player::getName).toList());
        assertNull(players.get(0).getUser());
        assertEquals(10, matchRepository.findAllByGroupIdOrderByPlayedAtDesc(groupA).size());
        assertEquals(0, userRepository.findByUsernameIgnoreCase("Alice").map(u -> 1).orElse(0));

        // Die Gruppe von Bob ist unberührt
        getAs(bob, "/api/groups/" + groupB + "/matches").andExpect(jsonPath("$", hasSize(10)));
    }
}
