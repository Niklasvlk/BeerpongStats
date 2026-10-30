package de.niklasvoelker.beerpongstats.config;

import de.niklasvoelker.beerpongstats.exception.GroupNotFoundException;
import de.niklasvoelker.beerpongstats.exception.NotGroupAdminException;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.model.Role;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroupAccessTest {

    private final PlayerRepository repository = mock(PlayerRepository.class);
    private final GroupAccess access = new GroupAccess(repository);

    private Player member(Role role) {
        Player player = Player.builder().id(20L).role(role).build();
        when(repository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.of(player));
        return player;
    }

    @Test
    void requireMember_shouldReturnTheMembership() {
        Player player = member(Role.MEMBER);

        assertSame(player, access.requireMember(10L, 1L));
    }

    @Test
    void requireMember_shouldRejectANonMemberLikeAMissingGroup() {
        when(repository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThrows(GroupNotFoundException.class, () -> access.requireMember(10L, 1L));
    }

    @Test
    void requireAdmin_shouldAcceptAnAdmin() {
        Player player = member(Role.ADMIN);

        assertSame(player, access.requireAdmin(10L, 1L));
    }

    @Test
    void requireAdmin_shouldRejectAPlainMember() {
        member(Role.MEMBER);

        assertThrows(NotGroupAdminException.class, () -> access.requireAdmin(10L, 1L));
    }

    @Test
    void requireAdmin_shouldHideTheGroupFromANonMember() {
        when(repository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThrows(GroupNotFoundException.class, () -> access.requireAdmin(10L, 1L));
    }
}
