package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.exception.GroupNotFoundException;
import de.niklasvoelker.beerpongstats.exception.UnknownUserException;
import de.niklasvoelker.beerpongstats.model.AppUser;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.model.PongGroup;
import de.niklasvoelker.beerpongstats.model.Role;
import de.niklasvoelker.beerpongstats.repository.AppUserRepository;
import de.niklasvoelker.beerpongstats.repository.GroupRepository;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import de.niklasvoelker.beerpongstats.service.GroupService.GroupMembership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private AppUserRepository userRepository;

    @InjectMocks
    private GroupService groupService;

    private AppUser max;

    @BeforeEach
    void setUp() {
        max = AppUser.builder().id(1L).username("Max").password("hash").build();
    }

    private void stubSaves() {
        when(groupRepository.save(any())).thenAnswer(invocation -> {
            PongGroup group = invocation.getArgument(0);
            group.setId(10L);
            return group;
        });
        when(playerRepository.save(any())).thenAnswer(invocation -> {
            Player player = invocation.getArgument(0);
            player.setId(20L);
            return player;
        });
    }

    @Test
    void createGroup_shouldMakeTheCreatorAdminWithTheirUsernameAsDefaultName() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(max));
        stubSaves();

        GroupMembership result = groupService.createGroup(1L, "  Kumpels ", null);

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(captor.capture());
        Player saved = captor.getValue();
        assertEquals("Max", saved.getName());
        assertEquals(Role.ADMIN, saved.getRole());
        assertSame(max, saved.getUser());
        assertEquals(10L, saved.getGroup().getId());
        assertEquals(0, saved.getWins());
        assertEquals(0, saved.getLosses());
        assertNotNull(saved.getCreatedAt());

        assertEquals("Kumpels", result.group().getName());
        assertNotNull(result.group().getCreatedAt());
        assertEquals(1, result.memberCount());
    }

    @Test
    void createGroup_shouldUseTheGivenDisplayName() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(max));
        stubSaves();

        groupService.createGroup(1L, "Kumpels", " Maxi ");

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(captor.capture());
        assertEquals("Maxi", captor.getValue().getName());
    }

    @Test
    void createGroup_shouldTreatABlankDisplayNameAsMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(max));
        stubSaves();

        groupService.createGroup(1L, "Kumpels", "   ");

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(captor.capture());
        assertEquals("Max", captor.getValue().getName());
    }

    @Test
    void createGroup_shouldRejectAnEmptyGroupName() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(max));

        assertThrows(IllegalArgumentException.class, () -> groupService.createGroup(1L, "  ", null));
        assertThrows(IllegalArgumentException.class, () -> groupService.createGroup(1L, null, null));

        verify(groupRepository, never()).save(any());
    }

    @Test
    void createGroup_shouldRejectTooLongNames() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(max));
        String longGroupName = "a".repeat(GroupService.MAX_GROUP_NAME_LENGTH + 1);
        String longDisplayName = "a".repeat(GroupService.MAX_DISPLAY_NAME_LENGTH + 1);

        assertThrows(IllegalArgumentException.class, () -> groupService.createGroup(1L, longGroupName, null));
        assertThrows(IllegalArgumentException.class, () -> groupService.createGroup(1L, "Kumpels", longDisplayName));

        verify(groupRepository, never()).save(any());
    }

    @Test
    void createGroup_shouldRejectASessionOfADeletedUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(UnknownUserException.class, () -> groupService.createGroup(1L, "Kumpels", null));

        verify(groupRepository, never()).save(any());
    }

    @Test
    void getMyGroups_shouldReturnEveryMembershipWithItsMemberCount() {
        PongGroup kumpels = PongGroup.builder().id(10L).name("Kumpels").build();
        PongGroup uni = PongGroup.builder().id(11L).name("Uni").build();
        Player inKumpels = Player.builder().id(20L).group(kumpels).role(Role.ADMIN).build();
        Player inUni = Player.builder().id(21L).group(uni).role(Role.MEMBER).build();
        when(playerRepository.findMemberships(1L)).thenReturn(List.of(inKumpels, inUni));
        when(playerRepository.countByGroupId(10L)).thenReturn(3L);
        when(playerRepository.countByGroupId(11L)).thenReturn(8L);

        List<GroupMembership> result = groupService.getMyGroups(1L);

        assertEquals(2, result.size());
        assertEquals("Kumpels", result.get(0).group().getName());
        assertEquals(3, result.get(0).memberCount());
        assertEquals(Role.MEMBER, result.get(1).player().getRole());
        assertEquals(8, result.get(1).memberCount());
    }

    @Test
    void getGroup_shouldReturnTheGroupForAMember() {
        PongGroup kumpels = PongGroup.builder().id(10L).name("Kumpels").build();
        Player me = Player.builder().id(20L).role(Role.MEMBER).build();
        when(playerRepository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.of(me));
        when(groupRepository.findById(10L)).thenReturn(Optional.of(kumpels));
        when(playerRepository.countByGroupId(10L)).thenReturn(4L);

        GroupMembership result = groupService.getGroup(10L, 1L);

        assertSame(kumpels, result.group());
        assertSame(me, result.player());
        assertEquals(4, result.memberCount());
    }

    @Test
    void getGroup_shouldTreatANonMemberLikeAMissingGroup() {
        when(playerRepository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThrows(GroupNotFoundException.class, () -> groupService.getGroup(10L, 1L));

        verify(groupRepository, never()).findById(any());
    }
}
