package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.config.GroupAccess;
import de.niklasvoelker.beerpongstats.config.GroupLimits;
import de.niklasvoelker.beerpongstats.exception.GroupConflictException;
import de.niklasvoelker.beerpongstats.exception.GroupNotFoundException;
import de.niklasvoelker.beerpongstats.exception.NotGroupAdminException;
import de.niklasvoelker.beerpongstats.exception.PlayerNotFoundByIDException;
import de.niklasvoelker.beerpongstats.exception.UnknownUserException;
import de.niklasvoelker.beerpongstats.model.AppUser;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.model.PongGroup;
import de.niklasvoelker.beerpongstats.model.Role;
import de.niklasvoelker.beerpongstats.repository.AppUserRepository;
import de.niklasvoelker.beerpongstats.repository.GroupRepository;
import de.niklasvoelker.beerpongstats.repository.InvitationRepository;
import de.niklasvoelker.beerpongstats.repository.MatchRepository;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import de.niklasvoelker.beerpongstats.service.GroupService.GroupMembership;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Sort;

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

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private GroupAccess groupAccess;

    private GroupService groupService;

    private AppUser max;

    @BeforeEach
    void setUp() {
        max = AppUser.builder().id(1L).username("Max").password("hash").build();
        groupService = serviceWith(new GroupLimits(20, 100));
    }

    private GroupService serviceWith(GroupLimits limits) {
        return new GroupService(groupRepository, playerRepository, userRepository, matchRepository,
                invitationRepository, groupAccess, limits);
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
        when(playerRepository.countByGroupIdAndUserIsNotNull(10L)).thenReturn(3L);
        when(playerRepository.countByGroupIdAndUserIsNotNull(11L)).thenReturn(8L);

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
        when(playerRepository.countByGroupIdAndUserIsNotNull(10L)).thenReturn(4L);

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

    // --- Mitglieder und Rollen ---

    private PongGroup group(Long id) {
        return PongGroup.builder().id(id).name("Kumpels").build();
    }

    private Player member(Long id, PongGroup group, Role role, AppUser user) {
        return Player.builder().id(id).group(group).role(role).user(user).name("P" + id).build();
    }

    @Test
    void getPlayers_shouldListEveryPlayerOfTheGroupSortedByName() {
        Player anna = member(1L, group(10L), Role.MEMBER, null);
        when(playerRepository.findByGroupId(10L, Sort.by(Sort.Direction.ASC, "name"))).thenReturn(List.of(anna));

        assertEquals(List.of(anna), groupService.getPlayers(10L));
    }

    @Test
    void changeRole_shouldPromoteAMember() {
        PongGroup g = group(10L);
        Player target = member(21L, g, Role.MEMBER, max);
        when(playerRepository.findByIdAndGroupId(21L, 10L)).thenReturn(Optional.of(target));

        Player result = groupService.changeRole(10L, 1L, 21L, Role.ADMIN);

        verify(groupAccess).requireAdmin(10L, 1L);
        assertEquals(Role.ADMIN, result.getRole());
    }

    @Test
    void changeRole_shouldRequireAnAdmin() {
        doThrow(new NotGroupAdminException()).when(groupAccess).requireAdmin(10L, 1L);

        assertThrows(NotGroupAdminException.class, () -> groupService.changeRole(10L, 1L, 21L, Role.ADMIN));

        verifyNoInteractions(playerRepository);
    }

    @Test
    void changeRole_shouldRejectAPlayerOfAnotherGroup() {
        when(playerRepository.findByIdAndGroupId(21L, 10L)).thenReturn(Optional.empty());

        assertThrows(PlayerNotFoundByIDException.class, () -> groupService.changeRole(10L, 1L, 21L, Role.ADMIN));
    }

    @Test
    void changeRole_shouldRejectAPlayerWithoutAccount() {
        Player former = member(21L, group(10L), Role.MEMBER, null);
        when(playerRepository.findByIdAndGroupId(21L, 10L)).thenReturn(Optional.of(former));

        assertThrows(GroupConflictException.class, () -> groupService.changeRole(10L, 1L, 21L, Role.ADMIN));

        assertEquals(Role.MEMBER, former.getRole());
    }

    @Test
    void changeRole_shouldNotDemoteTheLastAdmin() {
        Player admin = member(20L, group(10L), Role.ADMIN, max);
        when(playerRepository.findByIdAndGroupId(20L, 10L)).thenReturn(Optional.of(admin));
        when(playerRepository.countByGroupIdAndRoleAndUserIsNotNull(10L, Role.ADMIN)).thenReturn(1L);

        assertThrows(GroupConflictException.class, () -> groupService.changeRole(10L, 1L, 20L, Role.MEMBER));

        assertEquals(Role.ADMIN, admin.getRole());
    }

    @Test
    void changeRole_shouldDemoteAnAdminWhenAnotherOneRemains() {
        Player admin = member(20L, group(10L), Role.ADMIN, max);
        when(playerRepository.findByIdAndGroupId(20L, 10L)).thenReturn(Optional.of(admin));
        when(playerRepository.countByGroupIdAndRoleAndUserIsNotNull(10L, Role.ADMIN)).thenReturn(2L);

        groupService.changeRole(10L, 1L, 20L, Role.MEMBER);

        assertEquals(Role.MEMBER, admin.getRole());
    }

    // --- Verlassen ---

    @Test
    void leaveGroup_shouldKeepThePlayerWithoutAccount() {
        PongGroup g = group(10L);
        Player me = member(20L, g, Role.MEMBER, max);
        when(playerRepository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.of(me));
        when(playerRepository.countByGroupIdAndUserIsNotNull(10L)).thenReturn(3L);

        groupService.leaveGroup(10L, 1L);

        assertNull(me.getUser());
        assertEquals(Role.MEMBER, me.getRole());
        verify(groupRepository, never()).deleteGroup(any());
    }

    @Test
    void leaveGroup_shouldResetTheRoleOfAnAdminWhoLeaves() {
        Player me = member(20L, group(10L), Role.ADMIN, max);
        when(playerRepository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.of(me));
        when(playerRepository.countByGroupIdAndUserIsNotNull(10L)).thenReturn(3L);
        when(playerRepository.countByGroupIdAndRoleAndUserIsNotNull(10L, Role.ADMIN)).thenReturn(2L);

        groupService.leaveGroup(10L, 1L);

        assertNull(me.getUser());
        assertEquals(Role.MEMBER, me.getRole());
    }

    @Test
    void leaveGroup_shouldRefuseTheLastAdminWhileOthersRemain() {
        Player me = member(20L, group(10L), Role.ADMIN, max);
        when(playerRepository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.of(me));
        when(playerRepository.countByGroupIdAndUserIsNotNull(10L)).thenReturn(3L);
        when(playerRepository.countByGroupIdAndRoleAndUserIsNotNull(10L, Role.ADMIN)).thenReturn(1L);

        GroupConflictException ex = assertThrows(GroupConflictException.class, () -> groupService.leaveGroup(10L, 1L));

        assertTrue(ex.getMessage().contains("Kumpels"));
        assertSame(max, me.getUser());
        assertEquals(Role.ADMIN, me.getRole());
    }

    @Test
    void leaveGroup_shouldDeleteTheGroupWhenTheLastMemberLeaves() {
        Player me = member(20L, group(10L), Role.ADMIN, max);
        when(playerRepository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.of(me));
        when(playerRepository.countByGroupIdAndUserIsNotNull(10L)).thenReturn(1L);

        groupService.leaveGroup(10L, 1L);

        InOrder order = inOrder(matchRepository, invitationRepository, playerRepository, groupRepository);
        order.verify(matchRepository).deleteAllOfGroup(10L);
        order.verify(invitationRepository).deleteAllOfGroup(10L);
        order.verify(playerRepository).deleteAllOfGroup(10L);
        order.verify(groupRepository).deleteGroup(10L);
    }

    @Test
    void leaveGroup_shouldTreatANonMemberLikeAMissingGroup() {
        when(playerRepository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThrows(GroupNotFoundException.class, () -> groupService.leaveGroup(10L, 1L));
    }

    // --- Account löschen ---

    @Test
    void releaseMemberships_shouldDetachSharedGroupsAndDeleteOnesWithoutOtherMembers() {
        PongGroup shared = group(10L);
        PongGroup alone = PongGroup.builder().id(11L).name("Allein").build();
        Player inShared = member(20L, shared, Role.MEMBER, max);
        Player inAlone = member(21L, alone, Role.ADMIN, max);
        when(playerRepository.findMemberships(1L)).thenReturn(List.of(inShared, inAlone));
        when(playerRepository.countByGroupIdAndUserIsNotNull(10L)).thenReturn(4L);
        when(playerRepository.countByGroupIdAndUserIsNotNull(11L)).thenReturn(1L);

        groupService.releaseMemberships(1L);

        assertNull(inShared.getUser());
        verify(groupRepository).deleteGroup(11L);
        verify(groupRepository, never()).deleteGroup(10L);
    }

    @Test
    void releaseMemberships_shouldChangeNothingWhenOneGroupBlocksIt() {
        PongGroup shared = group(10L);
        PongGroup blocking = PongGroup.builder().id(11L).name("Team").build();
        Player inShared = member(20L, shared, Role.MEMBER, max);
        Player inBlocking = member(21L, blocking, Role.ADMIN, max);
        when(playerRepository.findMemberships(1L)).thenReturn(List.of(inShared, inBlocking));
        when(playerRepository.countByGroupIdAndUserIsNotNull(11L)).thenReturn(5L);
        when(playerRepository.countByGroupIdAndRoleAndUserIsNotNull(11L, Role.ADMIN)).thenReturn(1L);

        assertThrows(GroupConflictException.class, () -> groupService.releaseMemberships(1L));

        assertSame(max, inShared.getUser());
        assertSame(max, inBlocking.getUser());
        verify(groupRepository, never()).deleteGroup(any());
    }

    @Test
    void releaseMemberships_shouldDoNothingForAnAccountWithoutGroups() {
        when(playerRepository.findMemberships(1L)).thenReturn(List.of());

        groupService.releaseMemberships(1L);

        verify(groupRepository, never()).deleteGroup(any());
    }

    // --- Mitglieder entfernen ---

    private Player admin(Long id, PongGroup group) {
        Player admin = member(id, group, Role.ADMIN, max);
        when(groupAccess.requireAdmin(10L, 1L)).thenReturn(admin);
        return admin;
    }

    @Test
    void removeMember_shouldDetachTheMemberAndKeepThePlayer() {
        PongGroup g = group(10L);
        admin(20L, g);
        Player target = member(21L, g, Role.MEMBER, AppUser.builder().id(2L).build());
        when(playerRepository.findByIdAndGroupId(21L, 10L)).thenReturn(Optional.of(target));

        groupService.removeMember(10L, 1L, 21L);

        assertNull(target.getUser());
        assertEquals(Role.MEMBER, target.getRole());
        verify(playerRepository, never()).delete(any());
        verify(groupRepository, never()).deleteGroup(any());
    }

    @Test
    void removeMember_shouldRequireAnAdmin() {
        doThrow(new NotGroupAdminException()).when(groupAccess).requireAdmin(10L, 1L);

        assertThrows(NotGroupAdminException.class, () -> groupService.removeMember(10L, 1L, 21L));

        verifyNoInteractions(playerRepository);
    }

    @Test
    void removeMember_shouldNotFindAPlayerOfAnotherGroup() {
        admin(20L, group(10L));
        when(playerRepository.findByIdAndGroupId(21L, 10L)).thenReturn(Optional.empty());

        assertThrows(PlayerNotFoundByIDException.class, () -> groupService.removeMember(10L, 1L, 21L));
    }

    @Test
    void removeMember_shouldRefuseToRemoveYourself() {
        PongGroup g = group(10L);
        Player me = admin(20L, g);
        when(playerRepository.findByIdAndGroupId(20L, 10L)).thenReturn(Optional.of(me));

        assertThrows(GroupConflictException.class, () -> groupService.removeMember(10L, 1L, 20L));

        assertSame(max, me.getUser());
    }

    @Test
    void removeMember_shouldRefuseToRemoveAnotherAdmin() {
        PongGroup g = group(10L);
        admin(20L, g);
        Player otherAdmin = member(21L, g, Role.ADMIN, AppUser.builder().id(2L).build());
        when(playerRepository.findByIdAndGroupId(21L, 10L)).thenReturn(Optional.of(otherAdmin));

        GroupConflictException ex = assertThrows(GroupConflictException.class,
                () -> groupService.removeMember(10L, 1L, 21L));

        assertTrue(ex.getMessage().contains("herab"));
        assertNotNull(otherAdmin.getUser());
        assertEquals(Role.ADMIN, otherAdmin.getRole());
    }

    @Test
    void removeMember_shouldRefuseAPlayerWithoutAccount() {
        PongGroup g = group(10L);
        admin(20L, g);
        Player former = member(21L, g, Role.MEMBER, null);
        when(playerRepository.findByIdAndGroupId(21L, 10L)).thenReturn(Optional.of(former));

        assertThrows(GroupConflictException.class, () -> groupService.removeMember(10L, 1L, 21L));
    }

    // --- Umbenennen und löschen ---

    @Test
    void renameGroup_shouldTrimAndStoreTheName() {
        PongGroup g = group(10L);
        Player admin = admin(20L, g);
        when(groupRepository.findById(10L)).thenReturn(Optional.of(g));
        when(playerRepository.countByGroupIdAndUserIsNotNull(10L)).thenReturn(3L);

        GroupMembership result = groupService.renameGroup(10L, 1L, "  Neue Crew ");

        assertEquals("Neue Crew", g.getName());
        assertSame(g, result.group());
        assertSame(admin, result.player());
        assertEquals(3, result.memberCount());
    }

    @Test
    void renameGroup_shouldRequireAnAdmin() {
        doThrow(new NotGroupAdminException()).when(groupAccess).requireAdmin(10L, 1L);

        assertThrows(NotGroupAdminException.class, () -> groupService.renameGroup(10L, 1L, "Neu"));

        verifyNoInteractions(groupRepository);
    }

    @Test
    void renameGroup_shouldRejectInvalidNamesAndKeepTheOldOne() {
        PongGroup g = group(10L);
        admin(20L, g);
        when(groupRepository.findById(10L)).thenReturn(Optional.of(g));

        assertThrows(IllegalArgumentException.class, () -> groupService.renameGroup(10L, 1L, "  "));
        assertThrows(IllegalArgumentException.class, () -> groupService.renameGroup(10L, 1L, null));
        assertThrows(IllegalArgumentException.class,
                () -> groupService.renameGroup(10L, 1L, "a".repeat(GroupService.MAX_GROUP_NAME_LENGTH + 1)));

        assertEquals("Kumpels", g.getName());
    }

    @Test
    void deleteGroup_shouldRemoveEverythingOfTheGroupInOrder() {
        admin(20L, group(10L));

        groupService.deleteGroup(10L, 1L);

        InOrder order = inOrder(matchRepository, invitationRepository, playerRepository, groupRepository);
        order.verify(matchRepository).deleteAllOfGroup(10L);
        order.verify(invitationRepository).deleteAllOfGroup(10L);
        order.verify(playerRepository).deleteAllOfGroup(10L);
        order.verify(groupRepository).deleteGroup(10L);
    }

    @Test
    void deleteGroup_shouldRequireAnAdmin() {
        doThrow(new NotGroupAdminException()).when(groupAccess).requireAdmin(10L, 1L);

        assertThrows(NotGroupAdminException.class, () -> groupService.deleteGroup(10L, 1L));

        verifyNoInteractions(matchRepository, invitationRepository, groupRepository);
        verify(playerRepository, never()).deleteAllOfGroup(any());
    }

    // --- Limits ---

    @Test
    void createGroup_shouldRefuseAMemberOfTheMaximumNumberOfGroups() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(max));
        when(playerRepository.countByUserId(1L)).thenReturn(2L);

        GroupConflictException ex = assertThrows(GroupConflictException.class,
                () -> serviceWith(new GroupLimits(2, 100)).createGroup(1L, "Kumpels", null));

        assertTrue(ex.getMessage().contains("2"));
        verify(groupRepository, never()).save(any());
    }

    @Test
    void createGroup_shouldAllowOneGroupBelowTheLimit() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(max));
        when(playerRepository.countByUserId(1L)).thenReturn(1L);
        stubSaves();

        serviceWith(new GroupLimits(2, 100)).createGroup(1L, "Kumpels", null);

        verify(groupRepository).save(any());
    }
}
