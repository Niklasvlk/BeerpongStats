package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.config.GroupAccess;
import de.niklasvoelker.beerpongstats.config.GroupLimits;
import de.niklasvoelker.beerpongstats.exception.GroupConflictException;
import de.niklasvoelker.beerpongstats.exception.GroupNotFoundException;
import de.niklasvoelker.beerpongstats.exception.InvitationInvalidException;
import de.niklasvoelker.beerpongstats.exception.NotGroupAdminException;
import de.niklasvoelker.beerpongstats.exception.UnknownUserException;
import de.niklasvoelker.beerpongstats.model.AppUser;
import de.niklasvoelker.beerpongstats.model.Invitation;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.model.PongGroup;
import de.niklasvoelker.beerpongstats.model.Role;
import de.niklasvoelker.beerpongstats.repository.AppUserRepository;
import de.niklasvoelker.beerpongstats.repository.GroupRepository;
import de.niklasvoelker.beerpongstats.repository.InvitationRepository;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import de.niklasvoelker.beerpongstats.service.GroupService.GroupMembership;
import de.niklasvoelker.beerpongstats.service.InvitationService.CreatedInvitation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    private static final String TOKEN = "geheimer-token";

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private GroupAccess groupAccess;

    private InvitationService invitationService;

    private PongGroup group;
    private AppUser max;

    @BeforeEach
    void setUp() {
        group = PongGroup.builder().id(10L).name("Kumpels").build();
        max = AppUser.builder().id(1L).username("Max").build();
        invitationService = serviceWith(new GroupLimits(20, 100));
    }

    private InvitationService serviceWith(GroupLimits limits) {
        return new InvitationService(invitationRepository, groupRepository, playerRepository, userRepository,
                groupAccess, limits);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private Invitation invitation(LocalDateTime expiresAt, Integer maxUses, int uses, LocalDateTime revokedAt) {
        return Invitation.builder().id(5L).group(group).tokenHash(InvitationService.hash(TOKEN))
                .createdAt(now().minusDays(1)).expiresAt(expiresAt).maxUses(maxUses).uses(uses).revokedAt(revokedAt)
                .build();
    }

    private Invitation activeInvitation() {
        return invitation(now().plusDays(1), null, 0, null);
    }

    // --- Erstellen ---

    @Test
    void createInvitation_shouldStoreOnlyTheHashOfTheToken() {
        when(groupRepository.getReferenceById(10L)).thenReturn(group);
        when(invitationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreatedInvitation created = invitationService.createInvitation(10L, 1L, null, null);

        verify(groupAccess).requireAdmin(10L, 1L);
        ArgumentCaptor<Invitation> captor = ArgumentCaptor.forClass(Invitation.class);
        verify(invitationRepository).save(captor.capture());
        Invitation saved = captor.getValue();

        assertNotEquals(created.token(), saved.getTokenHash());
        assertEquals(InvitationService.hash(created.token()), saved.getTokenHash());
        assertEquals(64, saved.getTokenHash().length());
        // 32 Byte, URL-sicher kodiert und ohne Füllzeichen
        assertEquals(43, created.token().length());
        assertTrue(created.token().matches("[A-Za-z0-9_-]+"));
        assertSame(group, saved.getGroup());
        assertEquals(0, saved.getUses());
        assertNull(saved.getMaxUses());
        assertNull(saved.getRevokedAt());
    }

    @Test
    void createInvitation_shouldBeValidForSevenDaysByDefault() {
        when(invitationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Invitation invitation = invitationService.createInvitation(10L, 1L, null, null).invitation();

        long days = ChronoUnit.DAYS.between(now(), invitation.getExpiresAt().plusMinutes(1));
        assertEquals(7, days);
    }

    @Test
    void createInvitation_shouldTakeTheGivenLimits() {
        when(invitationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Invitation invitation = invitationService.createInvitation(10L, 1L, 2, 5).invitation();

        assertEquals(5, invitation.getMaxUses());
        assertEquals(2, ChronoUnit.DAYS.between(now(), invitation.getExpiresAt().plusMinutes(1)));
    }

    @Test
    void createInvitation_shouldCreateADifferentTokenEveryTime() {
        when(invitationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String first = invitationService.createInvitation(10L, 1L, null, null).token();
        String second = invitationService.createInvitation(10L, 1L, null, null).token();

        assertNotEquals(first, second);
    }

    @Test
    void createInvitation_shouldRejectInvalidLimits() {
        assertThrows(IllegalArgumentException.class, () -> invitationService.createInvitation(10L, 1L, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> invitationService.createInvitation(10L, 1L, InvitationService.MAX_VALID_DAYS + 1, null));
        assertThrows(IllegalArgumentException.class, () -> invitationService.createInvitation(10L, 1L, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> invitationService.createInvitation(10L, 1L, null, InvitationService.MAX_USES_LIMIT + 1));

        verify(invitationRepository, never()).save(any());
    }

    @Test
    void createInvitation_shouldRequireAnAdmin() {
        doThrow(new NotGroupAdminException()).when(groupAccess).requireAdmin(10L, 1L);

        assertThrows(NotGroupAdminException.class, () -> invitationService.createInvitation(10L, 1L, null, null));

        verify(invitationRepository, never()).save(any());
    }

    // --- Auflisten und Widerrufen ---

    @Test
    void getInvitations_shouldRequireAnAdmin() {
        doThrow(new NotGroupAdminException()).when(groupAccess).requireAdmin(10L, 1L);

        assertThrows(NotGroupAdminException.class, () -> invitationService.getInvitations(10L, 1L));

        verifyNoInteractions(invitationRepository);
    }

    @Test
    void getInvitations_shouldListTheInvitationsOfTheGroup() {
        Invitation invitation = activeInvitation();
        when(invitationRepository.findByGroupIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(invitation));

        assertEquals(List.of(invitation), invitationService.getInvitations(10L, 1L));
    }

    @Test
    void revoke_shouldMarkTheInvitationAsRevoked() {
        Invitation invitation = activeInvitation();
        when(invitationRepository.findByIdAndGroupId(5L, 10L)).thenReturn(Optional.of(invitation));

        invitationService.revoke(10L, 1L, 5L);

        verify(groupAccess).requireAdmin(10L, 1L);
        assertNotNull(invitation.getRevokedAt());
    }

    @Test
    void revoke_shouldKeepTheFirstRevocationTime() {
        LocalDateTime earlier = now().minusHours(2);
        Invitation invitation = invitation(now().plusDays(1), null, 0, earlier);
        when(invitationRepository.findByIdAndGroupId(5L, 10L)).thenReturn(Optional.of(invitation));

        invitationService.revoke(10L, 1L, 5L);

        assertEquals(earlier, invitation.getRevokedAt());
    }

    @Test
    void revoke_shouldNotFindAnInvitationOfAnotherGroup() {
        when(invitationRepository.findByIdAndGroupId(5L, 10L)).thenReturn(Optional.empty());

        assertThrows(GroupNotFoundException.class, () -> invitationService.revoke(10L, 1L, 5L));
    }

    // --- Vorschau ---

    @Test
    void preview_shouldShowTheGroupOfAValidInvitation() {
        when(invitationRepository.findByTokenHash(InvitationService.hash(TOKEN))).thenReturn(Optional.of(activeInvitation()));
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(playerRepository.countByGroupIdAndUserIsNotNull(10L)).thenReturn(4L);

        InvitationService.Preview preview = invitationService.preview(TOKEN);

        assertEquals("Kumpels", preview.groupName());
        assertEquals(4, preview.memberCount());
    }

    @Test
    void preview_shouldAnswerTheSameForEveryKindOfInvalidInvitation() {
        List<Invitation> invalid = List.of(
                invitation(now().minusMinutes(1), null, 0, null),   // abgelaufen
                invitation(now().plusDays(1), null, 0, now()),       // widerrufen
                invitation(now().plusDays(1), 3, 3, null));          // aufgebraucht

        for (Invitation invitation : invalid) {
            when(invitationRepository.findByTokenHash(InvitationService.hash(TOKEN))).thenReturn(Optional.of(invitation));

            InvitationInvalidException ex = assertThrows(InvitationInvalidException.class,
                    () -> invitationService.preview(TOKEN));
            assertEquals("Diese Einladung ist ungültig oder abgelaufen.", ex.getMessage());
        }

        when(invitationRepository.findByTokenHash(InvitationService.hash(TOKEN))).thenReturn(Optional.empty());
        assertThrows(InvitationInvalidException.class, () -> invitationService.preview(TOKEN));
    }

    // --- Beitreten ---

    private void stubValidAccept() {
        when(invitationRepository.findByTokenHash(InvitationService.hash(TOKEN))).thenReturn(Optional.of(activeInvitation()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(max));
        when(groupRepository.findForUpdate(10L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
    }

    @Test
    void accept_shouldCreateAMemberNamedLikeTheUsernameByDefault() {
        stubValidAccept();
        when(playerRepository.existsByGroupIdAndNameIgnoreCase(10L, "Max")).thenReturn(false);
        when(invitationRepository.consumeUse(eq(5L), any())).thenReturn(1);
        when(playerRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.countByGroupIdAndUserIsNotNull(10L)).thenReturn(2L);

        GroupMembership result = invitationService.accept(TOKEN, 1L, null);

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).saveAndFlush(captor.capture());
        Player saved = captor.getValue();
        assertEquals("Max", saved.getName());
        assertEquals(Role.MEMBER, saved.getRole());
        assertSame(max, saved.getUser());
        assertSame(group, saved.getGroup());
        assertEquals(0, saved.getWins());
        assertEquals(2, result.memberCount());
    }

    @Test
    void accept_shouldUseTheGivenDisplayName() {
        stubValidAccept();
        when(playerRepository.existsByGroupIdAndNameIgnoreCase(10L, "Maxi")).thenReturn(false);
        when(invitationRepository.consumeUse(eq(5L), any())).thenReturn(1);
        when(playerRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        invitationService.accept(TOKEN, 1L, " Maxi ");

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).saveAndFlush(captor.capture());
        assertEquals("Maxi", captor.getValue().getName());
    }

    @Test
    void accept_shouldRejectSomeoneWhoIsAlreadyAMemberWithoutUsingTheInvitation() {
        when(invitationRepository.findByTokenHash(InvitationService.hash(TOKEN))).thenReturn(Optional.of(activeInvitation()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(max));
        when(groupRepository.findForUpdate(10L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.of(new Player()));

        assertThrows(GroupConflictException.class, () -> invitationService.accept(TOKEN, 1L, null));

        verify(invitationRepository, never()).consumeUse(anyLong(), any());
        verify(playerRepository, never()).saveAndFlush(any());
    }

    @Test
    void accept_shouldRejectANameThatIsTakenInTheGroup() {
        stubValidAccept();
        when(playerRepository.existsByGroupIdAndNameIgnoreCase(10L, "Max")).thenReturn(true);

        GroupConflictException ex = assertThrows(GroupConflictException.class,
                () -> invitationService.accept(TOKEN, 1L, null));

        assertTrue(ex.getMessage().contains("Max"));
        verify(invitationRepository, never()).consumeUse(anyLong(), any());
        verify(playerRepository, never()).saveAndFlush(any());
    }

    @Test
    void accept_shouldRejectAnInvalidDisplayName() {
        stubValidAccept();

        assertThrows(IllegalArgumentException.class,
                () -> invitationService.accept(TOKEN, 1L, "a".repeat(GroupService.MAX_DISPLAY_NAME_LENGTH + 1)));

        verify(playerRepository, never()).saveAndFlush(any());
    }

    @Test
    void accept_shouldFailWhenTheLastUseWasTakenInTheMeantime() {
        stubValidAccept();
        when(playerRepository.existsByGroupIdAndNameIgnoreCase(10L, "Max")).thenReturn(false);
        when(invitationRepository.consumeUse(eq(5L), any())).thenReturn(0);

        assertThrows(InvitationInvalidException.class, () -> invitationService.accept(TOKEN, 1L, null));

        verify(playerRepository, never()).saveAndFlush(any());
    }

    @Test
    void accept_shouldReportAConflictWhenAnUniqueIndexRejectsTheInsert() {
        stubValidAccept();
        when(playerRepository.existsByGroupIdAndNameIgnoreCase(10L, "Max")).thenReturn(false);
        when(invitationRepository.consumeUse(eq(5L), any())).thenReturn(1);
        when(playerRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("ux_player_group_user"));

        assertThrows(GroupConflictException.class, () -> invitationService.accept(TOKEN, 1L, null));
    }

    @Test
    void accept_shouldRejectAnInvalidInvitationBeforeTouchingAnything() {
        when(invitationRepository.findByTokenHash(InvitationService.hash(TOKEN)))
                .thenReturn(Optional.of(invitation(now().minusMinutes(1), null, 0, null)));

        assertThrows(InvitationInvalidException.class, () -> invitationService.accept(TOKEN, 1L, null));

        verify(invitationRepository, never()).consumeUse(anyLong(), any());
        verifyNoInteractions(playerRepository);
    }

    @Test
    void accept_shouldRejectASessionOfADeletedUser() {
        when(invitationRepository.findByTokenHash(InvitationService.hash(TOKEN))).thenReturn(Optional.of(activeInvitation()));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(UnknownUserException.class, () -> invitationService.accept(TOKEN, 1L, null));

        verify(invitationRepository, never()).consumeUse(anyLong(), any());
    }

    // --- Obergrenzen und Sperre ---

    @Test
    void accept_shouldLockTheGroupBeforeCheckingAndUsingTheInvitation() {
        stubValidAccept();
        when(playerRepository.existsByGroupIdAndNameIgnoreCase(10L, "Max")).thenReturn(false);
        when(invitationRepository.consumeUse(eq(5L), any())).thenReturn(1);
        when(playerRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        invitationService.accept(TOKEN, 1L, null);

        InOrder order = inOrder(groupRepository, playerRepository, invitationRepository);
        order.verify(groupRepository).findForUpdate(10L);
        order.verify(playerRepository).countByGroupIdAndUserIsNotNull(10L);
        order.verify(invitationRepository).consumeUse(eq(5L), any());
    }

    @Test
    void accept_shouldRefuseAFullGroupWithoutUsingTheInvitation() {
        stubValidAccept();
        when(playerRepository.existsByGroupIdAndNameIgnoreCase(10L, "Max")).thenReturn(false);
        when(playerRepository.countByGroupIdAndUserIsNotNull(10L)).thenReturn(3L);

        GroupConflictException ex = assertThrows(GroupConflictException.class,
                () -> serviceWith(new GroupLimits(20, 3)).accept(TOKEN, 1L, null));

        assertEquals("Die Gruppe ist voll.", ex.getMessage());
        verify(invitationRepository, never()).consumeUse(anyLong(), any());
        verify(playerRepository, never()).saveAndFlush(any());
    }

    @Test
    void accept_shouldTakeTheLastFreeSlot() {
        stubValidAccept();
        when(playerRepository.existsByGroupIdAndNameIgnoreCase(10L, "Max")).thenReturn(false);
        when(playerRepository.countByGroupIdAndUserIsNotNull(10L)).thenReturn(2L);
        when(invitationRepository.consumeUse(eq(5L), any())).thenReturn(1);
        when(playerRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        serviceWith(new GroupLimits(20, 3)).accept(TOKEN, 1L, null);

        verify(playerRepository).saveAndFlush(any());
    }

    @Test
    void accept_shouldRefuseSomeoneWhoIsInTheMaximumNumberOfGroups() {
        stubValidAccept();
        when(playerRepository.existsByGroupIdAndNameIgnoreCase(10L, "Max")).thenReturn(false);
        when(playerRepository.countByUserId(1L)).thenReturn(2L);

        assertThrows(GroupConflictException.class, () -> serviceWith(new GroupLimits(2, 100)).accept(TOKEN, 1L, null));

        verify(invitationRepository, never()).consumeUse(anyLong(), any());
        verify(playerRepository, never()).saveAndFlush(any());
    }
}
