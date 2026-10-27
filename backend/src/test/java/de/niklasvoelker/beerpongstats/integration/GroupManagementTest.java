package de.niklasvoelker.beerpongstats.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mitglieder entfernen, Gruppe umbenennen und löschen, gegen echtes Postgres.
 *
 * <p>Aufbau: Alice ist Admin der Gruppe "Kumpels", Bob ist Mitglied, Carol hat einen Account ohne Gruppe.
 */
class GroupManagementTest extends ApiIntegrationTest {

    private MockHttpSession alice;
    private MockHttpSession bob;
    private MockHttpSession carol;
    private long group;
    private String base;
    private String token;
    private long bobPlayer;

    @BeforeEach
    void setUp() throws Exception {
        alice = registerAndLogin("Alice");
        bob = registerAndLogin("Bob");
        carol = registerAndLogin("Carol");
        group = createGroup(alice, "Kumpels", null);
        base = "/api/groups/" + group;
        token = createInvitation(alice, group, "{\"maxUses\":10}");
        accept(bob, token, null).andExpect(status().isCreated());
        bobPlayer = idFromList(alice, base + "/members", 1);
    }

    // --- Mitglieder entfernen ---

    @Test
    void anAdminCanRemoveAMemberWhoKeepsTheirMatches() throws Exception {
        playMatch(bob, group, "Bob", "Alice", 3);

        deleteAs(alice, base + "/members/" + bobPlayer).andExpect(status().isNoContent());

        getAs(bob, base).andExpect(status().isNotFound());
        getAs(bob, "/api/groups").andExpect(jsonPath("$", hasSize(0)));
        getAs(alice, base + "/members")
                .andExpect(jsonPath("$[1].name").value("Bob"))
                .andExpect(jsonPath("$[1].active").value(false))
                .andExpect(jsonPath("$[1].wins").value(1));
        getAs(alice, base + "/matches").andExpect(jsonPath("$", hasSize(1)));
        getAs(alice, "/api/groups").andExpect(jsonPath("$[0].memberCount").value(1));
    }

    @Test
    void aRemovedMemberCanJoinAgainWithAFreeNameWhileTheLinkIsValid() throws Exception {
        deleteAs(alice, base + "/members/" + bobPlayer).andExpect(status().isNoContent());

        accept(bob, token, null).andExpect(status().isConflict());
        accept(bob, token, "Bob neu").andExpect(status().isCreated());

        getAs(bob, base).andExpect(status().isOk());
    }

    @Test
    void aMemberCannotRemoveAnyone() throws Exception {
        long alicePlayer = idFromList(bob, base + "/members", 0);

        deleteAs(bob, base + "/members/" + alicePlayer).andExpect(status().isForbidden());
        deleteAs(bob, base + "/members/" + bobPlayer).andExpect(status().isForbidden());

        getAs(alice, base + "/members").andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].active").value(true));
    }

    @Test
    void anAdminMustBeDemotedBeforeBeingRemoved() throws Exception {
        patchAs(alice, base + "/members/" + bobPlayer, "{\"role\":\"ADMIN\"}").andExpect(status().isOk());

        deleteAs(alice, base + "/members/" + bobPlayer).andExpect(status().isConflict());
        getAs(alice, base + "/members").andExpect(jsonPath("$[1].active").value(true));

        patchAs(alice, base + "/members/" + bobPlayer, "{\"role\":\"MEMBER\"}").andExpect(status().isOk());
        deleteAs(alice, base + "/members/" + bobPlayer).andExpect(status().isNoContent());
    }

    @Test
    void anAdminCannotRemoveThemselves() throws Exception {
        long alicePlayer = idFromList(alice, base + "/members", 0);

        deleteAs(alice, base + "/members/" + alicePlayer).andExpect(status().isConflict());

        getAs(alice, base).andExpect(status().isOk());
    }

    @Test
    void thereIsNothingToRemoveForPlayersWithoutAccountOrOfAnotherGroup() throws Exception {
        addPlaceholder(group, "Gast");
        long guest = idFromList(alice, base + "/members", 2);
        long otherGroup = createGroup(carol, "Andere", null);
        long foreign = idFromList(carol, "/api/groups/" + otherGroup + "/members", 0);

        deleteAs(alice, base + "/members/" + guest).andExpect(status().isConflict());
        deleteAs(alice, base + "/members/" + foreign).andExpect(status().isNotFound());
        getAs(carol, "/api/groups/" + otherGroup + "/members").andExpect(jsonPath("$[0].active").value(true));
    }

    // --- Umbenennen ---

    @Test
    void anAdminCanRenameTheGroupForEveryone() throws Exception {
        patchAs(alice, base, "{\"name\":\"  Neue Crew \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Neue Crew"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        getAs(bob, "/api/groups").andExpect(jsonPath("$[0].name").value("Neue Crew"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/invitations/" + token))
                .andExpect(jsonPath("$.groupName").value("Neue Crew"));
    }

    @Test
    void onlyAdminsCanRenameAndTheNameMustBeValid() throws Exception {
        patchAs(bob, base, "{\"name\":\"Gekapert\"}").andExpect(status().isForbidden());
        patchAs(carol, base, "{\"name\":\"Gekapert\"}").andExpect(status().isNotFound());
        patchAs(alice, base, "{\"name\":\"  \"}").andExpect(status().isBadRequest());
        patchAs(alice, base, "{\"name\":\"" + "a".repeat(51) + "\"}").andExpect(status().isBadRequest());

        assertEquals("Kumpels", groupRepository.findById(group).orElseThrow().getName());
    }

    // --- Gruppe löschen ---

    @Test
    void anAdminCanDeleteTheGroupWithEverythingInIt() throws Exception {
        playMatch(bob, group, "Bob", "Alice", 3);
        long otherGroup = createGroup(carol, "Andere", null);
        addPlaceholder(otherGroup, "Gast");
        playMatch(carol, otherGroup, "Carol", "Gast", 2);

        deleteAs(alice, base).andExpect(status().isNoContent());

        assertFalse(groupRepository.existsById(group));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM player WHERE group_id = ?", Integer.class, group));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM match WHERE group_id = ?", Integer.class, group));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM invitation WHERE group_id = ?", Integer.class, group));
        // Die Accounts bleiben bestehen, der Link funktioniert nicht mehr
        getAs(alice, "/api/groups").andExpect(jsonPath("$", hasSize(0)));
        getAs(bob, "/api/groups").andExpect(jsonPath("$", hasSize(0)));
        accept(carol, token, null).andExpect(status().isGone());
        assertTrue(userRepository.findByUsernameIgnoreCase("Bob").isPresent());
        // Eine andere Gruppe ist unberührt
        getAs(carol, "/api/groups/" + otherGroup + "/matches").andExpect(jsonPath("$", hasSize(1)));
        getAs(carol, "/api/groups/" + otherGroup + "/players").andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void onlyAdminsCanDeleteTheGroup() throws Exception {
        deleteAs(bob, base).andExpect(status().isForbidden());
        deleteAs(carol, base).andExpect(status().isNotFound());

        assertTrue(groupRepository.existsById(group));
        getAs(alice, base + "/members").andExpect(jsonPath("$", hasSize(2)));
    }
}
