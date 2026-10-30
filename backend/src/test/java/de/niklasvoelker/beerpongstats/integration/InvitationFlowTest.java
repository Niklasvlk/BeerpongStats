package de.niklasvoelker.beerpongstats.integration;

import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Der Weg in eine Gruppe über Einladungslinks und die Verwaltung der Mitglieder, gegen echtes Postgres.
 *
 * <p>Aufbau: Alice legt die Gruppe "Kumpels" an und ist deren Admin. Bob und Carol haben einen Account, aber keine
 * Gruppe.
 */
class InvitationFlowTest extends ApiIntegrationTest {

    private MockHttpSession alice;
    private MockHttpSession bob;
    private MockHttpSession carol;
    private long group;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        alice = registerAndLogin("Alice");
        bob = registerAndLogin("Bob");
        carol = registerAndLogin("Carol");
        group = createGroup(alice, "Kumpels", null);
        base = "/api/groups/" + group;
    }

    private void join(MockHttpSession session, String token) throws Exception {
        accept(session, token, null).andExpect(status().isCreated());
    }

    // --- Der Ablauf über den Link ---

    @Test
    void anInviteeRegistersViaTheLinkAndJoinsTheGroup() throws Exception {
        String token = createInvitation(alice, group, null);

        // Die Vorschau braucht keinen Login
        mockMvc.perform(get("/api/invitations/" + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.groupName").value("Kumpels"))
                .andExpect(jsonPath("$.memberCount").value(1));
        // Beitreten schon
        mockMvc.perform(post("/api/invitations/" + token + "/accept").with(csrf())).andExpect(status().isUnauthorized());

        MockHttpSession dave = registerAndLogin("Dave");
        accept(dave, token, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(group))
                .andExpect(jsonPath("$.name").value("Kumpels"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.memberCount").value(2));

        getAs(dave, "/api/groups").andExpect(jsonPath("$", hasSize(1)));
        getAs(dave, base + "/players?sort=name").andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.contains("Alice", "Dave")));
        getAs(alice, base + "/members").andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/invitations/" + token)).andExpect(jsonPath("$.memberCount").value(2));
    }

    @Test
    void aNewMemberCanPlayMatchesWithTheOthers() throws Exception {
        join(bob, createInvitation(alice, group, null));

        playMatch(bob, group, "Bob", "Alice", 3);

        getAs(alice, base + "/matches").andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].winner1.name").value("Bob"));
        getAs(alice, base + "/players?sort=name")
                .andExpect(jsonPath("$[0].name").value("Alice")).andExpect(jsonPath("$[0].losses").value(1))
                .andExpect(jsonPath("$[1].name").value("Bob")).andExpect(jsonPath("$[1].wins").value(1));
    }

    @Test
    void theTokenIsNeverStoredInPlainText() throws Exception {
        String token = createInvitation(alice, group, null);

        String stored = jdbc.queryForObject("SELECT token_hash FROM invitation", String.class);

        assertNotEquals(token, stored);
        assertEquals(64, stored.length());
        assertFalse(stored.contains(token));
    }

    @Test
    void theListShowsStatusAndUsesButNeverTheToken() throws Exception {
        String token = createInvitation(alice, group, "{\"maxUses\":2}");
        join(bob, token);

        MvcResult result = getAs(alice, base + "/invitations").andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].uses").value(1))
                .andExpect(jsonPath("$[0].maxUses").value(2))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains(token));
    }

    // --- Wann ein Link nicht (mehr) funktioniert ---

    @Test
    void aRevokedInvitationCannotBeUsed() throws Exception {
        String token = createInvitation(alice, group, null);
        long id = idFromList(alice, base + "/invitations", 0);

        deleteAs(alice, base + "/invitations/" + id).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/invitations/" + token)).andExpect(status().isGone());
        accept(bob, token, null).andExpect(status().isGone());
        getAs(alice, base + "/invitations").andExpect(jsonPath("$[0].status").value("REVOKED"));
        getAs(bob, "/api/groups").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void anExpiredInvitationCannotBeUsed() throws Exception {
        String token = createInvitation(alice, group, null);

        expireAllInvitations();

        mockMvc.perform(get("/api/invitations/" + token)).andExpect(status().isGone());
        accept(bob, token, null).andExpect(status().isGone());
        getAs(alice, base + "/invitations").andExpect(jsonPath("$[0].status").value("EXPIRED"));
    }

    @Test
    void anInvitationWithALimitStopsWorkingWhenItIsUsedUp() throws Exception {
        String token = createInvitation(alice, group, "{\"maxUses\":1}");

        join(bob, token);

        accept(carol, token, null).andExpect(status().isGone());
        mockMvc.perform(get("/api/invitations/" + token)).andExpect(status().isGone());
        getAs(alice, base + "/invitations").andExpect(jsonPath("$[0].status").value("EXHAUSTED"))
                .andExpect(jsonPath("$[0].uses").value(1));
        getAs(carol, "/api/groups").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void everyKindOfInvalidInvitationGetsTheSameAnswer() throws Exception {
        String expired = createInvitation(alice, group, null);
        expireAllInvitations();

        String expiredAnswer = read(accept(bob, expired, null).andExpect(status().isGone()), "$.detail");
        String unknownAnswer = read(accept(bob, "gibt-es-nicht", null).andExpect(status().isGone()), "$.detail");

        assertEquals(expiredAnswer, unknownAnswer);
        mockMvc.perform(get("/api/invitations/gibt-es-nicht")).andExpect(status().isGone());
    }

    @Test
    void parallelAcceptsNeverExceedTheLimit() throws Exception {
        int contenders = 8;
        int limit = 3;
        String token = createInvitation(alice, group, "{\"maxUses\":" + limit + "}");
        List<MockHttpSession> sessions = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            sessions.add(registerAndLogin("Spieler" + i));
        }

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CyclicBarrier start = new CyclicBarrier(contenders);
        List<Future<Integer>> results = new ArrayList<>();
        for (MockHttpSession session : sessions) {
            results.add(pool.submit(() -> {
                start.await();
                return accept(session, token, null).andReturn().getResponse().getStatus();
            }));
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();

        assertEquals(limit, statuses.stream().filter(s -> s == 201).count(), "Beigetreten: " + statuses);
        assertEquals(contenders - limit, statuses.stream().filter(s -> s == 410).count(), "Abgelehnt: " + statuses);
        assertEquals(1 + limit, playerRepository.countByGroupIdAndUserIsNotNull(group));
        assertEquals(limit, jdbc.queryForObject("SELECT uses FROM invitation", Integer.class));
    }

    // --- Konflikte beim Beitreten ---

    @Test
    void joiningTwiceIsRejectedAndDoesNotUseUpTheInvitation() throws Exception {
        String token = createInvitation(alice, group, "{\"maxUses\":5}");
        join(bob, token);

        accept(bob, token, "Bob zwei").andExpect(status().isConflict());

        assertEquals(1, jdbc.queryForObject("SELECT uses FROM invitation", Integer.class));
        assertEquals(2, playerRepository.countByGroupIdAndUserIsNotNull(group));
    }

    @Test
    void aTakenNameIsRejectedIgnoringCaseAndAnotherNameWorks() throws Exception {
        String token = createInvitation(alice, group, null);

        accept(bob, token, "alice").andExpect(status().isConflict());
        assertEquals(0, jdbc.queryForObject("SELECT uses FROM invitation", Integer.class));

        accept(bob, token, "Bobby").andExpect(status().isCreated());
        getAs(alice, base + "/players?sort=name").andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.contains("Alice", "Bobby")));
    }

    @Test
    void aTooLongDisplayNameIsRejected() throws Exception {
        String token = createInvitation(alice, group, null);

        accept(bob, token, "a".repeat(31)).andExpect(status().isBadRequest());

        assertEquals(0, jdbc.queryForObject("SELECT uses FROM invitation", Integer.class));
    }

    // --- Wer Einladungen verwalten darf ---

    @Test
    void onlyAdminsManageInvitationsAndRoles() throws Exception {
        join(bob, createInvitation(alice, group, null));
        long invitationId = idFromList(alice, base + "/invitations", 0);
        long bobPlayer = idFromList(alice, base + "/members", 1);

        postAs(bob, base + "/invitations", null).andExpect(status().isForbidden());
        getAs(bob, base + "/invitations").andExpect(status().isForbidden());
        deleteAs(bob, base + "/invitations/" + invitationId).andExpect(status().isForbidden());
        patchAs(bob, base + "/members/" + bobPlayer, "{\"role\":\"ADMIN\"}").andExpect(status().isForbidden());

        // Lesen dürfen Mitglieder
        getAs(bob, base + "/members").andExpect(status().isOk());
        assertEquals(Role.MEMBER, playerRepository.findById(bobPlayer).orElseThrow().getRole());
    }

    @Test
    void invalidInvitationLimitsAreRejected() throws Exception {
        postAs(alice, base + "/invitations", "{\"expiresInDays\":0}").andExpect(status().isBadRequest());
        postAs(alice, base + "/invitations", "{\"expiresInDays\":31}").andExpect(status().isBadRequest());
        postAs(alice, base + "/invitations", "{\"maxUses\":0}").andExpect(status().isBadRequest());
        postAs(alice, base + "/invitations", "{\"maxUses\":101}").andExpect(status().isBadRequest());

        assertEquals(0, invitationRepository.count());
    }

    @Test
    void anInvitationOfAnotherGroupCannotBeRevokedThroughYourOwn() throws Exception {
        createInvitation(alice, group, null);
        long invitationId = idFromList(alice, base + "/invitations", 0);
        long otherGroup = createGroup(bob, "Andere", null);

        deleteAs(bob, "/api/groups/" + otherGroup + "/invitations/" + invitationId).andExpect(status().isNotFound());

        getAs(alice, base + "/invitations").andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    // --- Rollen ---

    @Test
    void anAdminCanPromoteAndDemoteMembers() throws Exception {
        join(bob, createInvitation(alice, group, null));
        long bobPlayer = idFromList(alice, base + "/members", 1);

        patchAs(alice, base + "/members/" + bobPlayer, "{\"role\":\"ADMIN\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        // Bob ist jetzt selbst Admin
        postAs(bob, base + "/invitations", null).andExpect(status().isCreated());
        patchAs(bob, base + "/members/" + bobPlayer, "{\"role\":\"MEMBER\"}").andExpect(status().isOk());
        postAs(bob, base + "/invitations", null).andExpect(status().isForbidden());
    }

    @Test
    void theLastAdminCannotBeDemoted() throws Exception {
        long alicePlayer = idFromList(alice, base + "/members", 0);

        patchAs(alice, base + "/members/" + alicePlayer, "{\"role\":\"MEMBER\"}").andExpect(status().isConflict());

        assertEquals(Role.ADMIN, playerRepository.findById(alicePlayer).orElseThrow().getRole());
    }

    @Test
    void aRoleCanOnlyBeGivenToMembersWithAnAccountOfThisGroup() throws Exception {
        addPlaceholder(group, "Gast");
        long guest = idFromList(alice, base + "/members?x=1", 1);
        long otherGroup = createGroup(bob, "Andere", null);
        long foreign = idFromList(bob, "/api/groups/" + otherGroup + "/members", 0);

        patchAs(alice, base + "/members/" + guest, "{\"role\":\"ADMIN\"}").andExpect(status().isConflict());
        patchAs(alice, base + "/members/" + foreign, "{\"role\":\"ADMIN\"}").andExpect(status().isNotFound());
        patchAs(alice, base + "/members/" + guest, "{\"role\":\"KING\"}").andExpect(status().isBadRequest());
    }

    // --- Gruppe verlassen ---

    @Test
    void leavingKeepsThePlayerAndItsMatchesWithoutAnAccount() throws Exception {
        join(bob, createInvitation(alice, group, null));
        playMatch(bob, group, "Bob", "Alice", 3);

        deleteAs(bob, base + "/members/me").andExpect(status().isNoContent());

        // Bob sieht die Gruppe nicht mehr
        getAs(bob, base).andExpect(status().isNotFound());
        getAs(bob, "/api/groups").andExpect(jsonPath("$", hasSize(0)));
        // Für die anderen bleibt sein Spieler samt Match erhalten, aber ohne Account
        getAs(alice, base + "/members")
                .andExpect(jsonPath("$[1].name").value("Bob"))
                .andExpect(jsonPath("$[1].active").value(false))
                .andExpect(jsonPath("$[1].role").value("MEMBER"))
                .andExpect(jsonPath("$[1].wins").value(1));
        getAs(alice, base + "/matches").andExpect(jsonPath("$", hasSize(1)));
        getAs(alice, "/api/groups").andExpect(jsonPath("$[0].memberCount").value(1));
    }

    @Test
    void someoneWhoLeftCanJoinAgainWithAFreeName() throws Exception {
        String token = createInvitation(alice, group, "{\"maxUses\":5}");
        join(bob, token);
        deleteAs(bob, base + "/members/me").andExpect(status().isNoContent());

        // Der alte Spieler "Bob" gehört weiter zur Gruppe, der Name ist also belegt
        accept(bob, token, null).andExpect(status().isConflict());
        accept(bob, token, "Bob neu").andExpect(status().isCreated());

        getAs(bob, "/api/groups").andExpect(jsonPath("$", hasSize(1)));
        getAs(alice, base + "/members").andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void theLastAdminMustHandOverBeforeLeaving() throws Exception {
        join(bob, createInvitation(alice, group, null));
        long bobPlayer = idFromList(alice, base + "/members", 1);

        deleteAs(alice, base + "/members/me").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Kumpels")));
        getAs(alice, base).andExpect(status().isOk());

        patchAs(alice, base + "/members/" + bobPlayer, "{\"role\":\"ADMIN\"}").andExpect(status().isOk());
        deleteAs(alice, base + "/members/me").andExpect(status().isNoContent());

        getAs(bob, base).andExpect(jsonPath("$.role").value("ADMIN")).andExpect(jsonPath("$.memberCount").value(1));
    }

    @Test
    void theLastMemberLeavingDeletesTheGroupWithEverythingInIt() throws Exception {
        addPlaceholder(group, "Gast");
        playMatch(alice, group, "Alice", "Gast", 4);
        createInvitation(alice, group, null);

        deleteAs(alice, base + "/members/me").andExpect(status().isNoContent());

        assertFalse(groupRepository.existsById(group));
        assertEquals(0, playerRepository.count());
        assertEquals(0, matchRepository.count());
        assertEquals(0, invitationRepository.count());
    }

    // --- Account löschen ---

    @Test
    void anAdminWithOtherMembersCannotDeleteTheAccountUntilSomeoneTakesOver() throws Exception {
        join(bob, createInvitation(alice, group, null));
        long bobPlayer = idFromList(alice, base + "/members", 1);

        deleteAs(alice, "/api/auth/me").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Kumpels")));

        // Nichts wurde verändert: Alice ist noch da und noch Admin
        getAs(alice, base).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        login("Alice");

        patchAs(alice, base + "/members/" + bobPlayer, "{\"role\":\"ADMIN\"}").andExpect(status().isOk());
        deleteAs(alice, "/api/auth/me").andExpect(status().isNoContent());

        List<Player> players = playerRepository.findByGroupId(group, Sort.by("name"));
        assertEquals(List.of("Alice", "Bob"), players.stream().map(Player::getName).toList());
        assertNull(players.get(0).getUser());
        assertNotNull(players.get(1).getUser());
        getAs(bob, base + "/members").andExpect(jsonPath("$[0].active").value(false));
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json("username", "Alice", "password", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletingTheAccountKeepsSharedGroupsAndDeletesTheOnesWithoutOtherMembers() throws Exception {
        join(bob, createInvitation(alice, group, null));
        playMatch(bob, group, "Bob", "Alice", 2);
        long alone = createGroup(bob, "Allein", null);
        addPlaceholder(alone, "Gast");
        playMatch(bob, alone, "Bob", "Gast", 1);

        deleteAs(bob, "/api/auth/me").andExpect(status().isNoContent());

        // Die gemeinsame Gruppe bleibt mit Bobs Spieler und seinem Match
        getAs(alice, base + "/matches").andExpect(jsonPath("$", hasSize(1)));
        getAs(alice, base + "/members")
                .andExpect(jsonPath("$[1].name").value("Bob"))
                .andExpect(jsonPath("$[1].active").value(false));
        // Die Gruppe, in der Bob allein war, ist samt Matches weg
        assertFalse(groupRepository.existsById(alone));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM match WHERE group_id = ?", Integer.class, alone));
        assertTrue(groupRepository.existsById(group));
    }

    @Test
    void aNonMemberCannotLeaveOrManageAGroup() throws Exception {
        deleteAs(bob, base + "/members/me").andExpect(status().isNotFound());
        postAs(bob, base + "/invitations", null).andExpect(status().isNotFound());
        getAs(bob, base + "/members").andExpect(status().isNotFound());
        getAs(bob, base).andExpect(jsonPath("$.detail").value(not(containsString("Alice"))));
    }
}
