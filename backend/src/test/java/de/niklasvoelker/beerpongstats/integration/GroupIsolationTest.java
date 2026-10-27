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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
class GroupIsolationTest extends ApiIntegrationTest {

    private MockHttpSession alice;
    private MockHttpSession bob;
    private MockHttpSession carol;
    private long groupA;
    private long groupB;

    @BeforeEach
    void setUp() throws Exception {
        alice = registerAndLogin("Alice");
        bob = registerAndLogin("Bob");
        carol = registerAndLogin("Carol");

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
                // Mitglieder sind Spieler mit Account: Anna und Max zählen nicht mit
                .andExpect(jsonPath("$[0].memberCount").value(1));
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
            getAs(outsider, a + "/members").andExpect(status().isNotFound());
            patchAs(outsider, a + "/members/" + annaId, "{\"role\":\"ADMIN\"}").andExpect(status().isNotFound());
            deleteAs(outsider, a + "/members/me").andExpect(status().isNotFound());
            patchAs(outsider, a, "{\"name\":\"Gekapert\"}").andExpect(status().isNotFound());
            deleteAs(outsider, a).andExpect(status().isNotFound());
            deleteAs(outsider, a + "/members/" + annaId).andExpect(status().isNotFound());
            getAs(outsider, a + "/invitations").andExpect(status().isNotFound());
            postAs(outsider, a + "/invitations", null).andExpect(status().isNotFound());
            deleteAs(outsider, a + "/invitations/1").andExpect(status().isNotFound());
            mockMvc.perform(post(a + "/matches").with(csrf()).session(outsider)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"winner1\":\"Alice\",\"loser1\":\"Anna\",\"winner1Cups\":10,\"loser1Cups\":3}"))
                    .andExpect(status().isNotFound());
        }

        // Nichts davon hat die Gruppe verändert
        assertEquals(10, matchRepository.findAllByGroupIdOrderByPlayedAtDesc(groupA).size());
        assertEquals("Gruppe A", groupRepository.findById(groupA).orElseThrow().getName());
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
    void deletingTheAccountOfTheOnlyMemberDeletesTheGroupAndNothingElse() throws Exception {
        // Alice ist in Gruppe A das einzige Mitglied mit Account (Anna und Max haben keinen)
        mockMvc.perform(delete("/api/auth/me").with(csrf()).session(alice)).andExpect(status().isNoContent());

        assertEquals(0, userRepository.findByUsernameIgnoreCase("Alice").map(u -> 1).orElse(0));
        assertFalse(groupRepository.existsById(groupA));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM player WHERE group_id = ?", Integer.class, groupA));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM match WHERE group_id = ?", Integer.class, groupA));

        // Die Gruppe von Bob ist unberührt
        getAs(bob, "/api/groups/" + groupB + "/matches").andExpect(jsonPath("$", hasSize(10)));
        getAs(bob, "/api/groups/" + groupB + "/players").andExpect(jsonPath("$", hasSize(3)));
    }
}
