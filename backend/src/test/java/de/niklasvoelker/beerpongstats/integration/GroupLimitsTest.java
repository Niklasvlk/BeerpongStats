package de.niklasvoelker.beerpongstats.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Die Obergrenzen für Gruppen pro Account und Mitglieder pro Gruppe, hier bewusst klein eingestellt. */
@TestPropertySource(properties = {
        "beerpong.limits.max-groups-per-user=2",
        "beerpong.limits.max-members-per-group=3"
})
class GroupLimitsTest extends ApiIntegrationTest {

    private MockHttpSession alice;

    @BeforeEach
    void setUp() throws Exception {
        alice = registerAndLogin("Alice");
    }

    private void addMembers(long group, String token, String... names) throws Exception {
        for (String name : names) {
            accept(registerAndLogin(name), token, null).andExpect(status().isCreated());
        }
    }

    // --- Gruppen pro Account ---

    @Test
    void anAccountCanCreateGroupsOnlyUpToTheLimit() throws Exception {
        createGroup(alice, "Eins", null);
        createGroup(alice, "Zwei", null);

        postAs(alice, "/api/groups", "{\"name\":\"Drei\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Du kannst höchstens in 2 Gruppen Mitglied sein."));

        getAs(alice, "/api/groups").andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void joiningCountsTowardsTheLimitToo() throws Exception {
        MockHttpSession bob = registerAndLogin("Bob");
        long first = createGroup(bob, "Von Bob", null);
        long second = createGroup(bob, "Noch eine", null);
        String firstLink = createInvitation(bob, first, null);
        String secondLink = createInvitation(bob, second, null);

        createGroup(alice, "Eigene", null);
        accept(alice, firstLink, null).andExpect(status().isCreated());

        accept(alice, secondLink, null).andExpect(status().isConflict());
        assertEquals(0, jdbc.queryForObject("SELECT uses FROM invitation WHERE group_id = ?", Integer.class, second));
        getAs(alice, "/api/groups").andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void leavingAGroupMakesRoomForAnother() throws Exception {
        MockHttpSession bob = registerAndLogin("Bob");
        long shared = createGroup(bob, "Von Bob", null);
        accept(alice, createInvitation(bob, shared, null), null).andExpect(status().isCreated());
        createGroup(alice, "Eigene", null);
        postAs(alice, "/api/groups", "{\"name\":\"Zu viel\"}").andExpect(status().isConflict());

        deleteAs(alice, "/api/groups/" + shared + "/members/me").andExpect(status().isNoContent());

        postAs(alice, "/api/groups", "{\"name\":\"Jetzt ok\"}").andExpect(status().isCreated());
    }

    // --- Mitglieder pro Gruppe ---

    @Test
    void aFullGroupTakesNoMoreMembersAndDoesNotUseUpTheInvitation() throws Exception {
        long group = createGroup(alice, "Kumpels", null);
        String token = createInvitation(alice, group, null);
        addMembers(group, token, "Bob", "Carol");
        MockHttpSession dave = registerAndLogin("Dave");

        accept(dave, token, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die Gruppe ist voll."));

        assertEquals(2, jdbc.queryForObject("SELECT uses FROM invitation", Integer.class));
        assertEquals(3, playerRepository.countByGroupIdAndUserIsNotNull(group));
        getAs(dave, "/api/groups").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void leavingOrBeingRemovedMakesRoomInAFullGroup() throws Exception {
        long group = createGroup(alice, "Kumpels", null);
        String token = createInvitation(alice, group, null);
        MockHttpSession bob = registerAndLogin("Bob");
        accept(bob, token, null).andExpect(status().isCreated());
        addMembers(group, token, "Carol");
        MockHttpSession dave = registerAndLogin("Dave");
        accept(dave, token, null).andExpect(status().isConflict());

        deleteAs(bob, "/api/groups/" + group + "/members/me").andExpect(status().isNoContent());

        accept(dave, token, null).andExpect(status().isCreated());
    }

    @Test
    void parallelJoinsCannotOverfillAGroup() throws Exception {
        long group = createGroup(alice, "Kumpels", null);
        String token = createInvitation(alice, group, null);
        addMembers(group, token, "Bob"); // 2 von 3 Plätzen belegt
        int contenders = 6;
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

        assertEquals(1, statuses.stream().filter(s -> s == 201).count(), "Beigetreten: " + statuses);
        assertEquals(contenders - 1, statuses.stream().filter(s -> s == 409).count(), "Abgelehnt: " + statuses);
        assertEquals(3, playerRepository.countByGroupIdAndUserIsNotNull(group));
        assertEquals(2, jdbc.queryForObject("SELECT uses FROM invitation", Integer.class));
    }
}
