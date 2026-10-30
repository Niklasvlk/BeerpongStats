package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.config.GroupAccess;
import de.niklasvoelker.beerpongstats.config.GroupLimits;
import de.niklasvoelker.beerpongstats.exception.GroupConflictException;
import de.niklasvoelker.beerpongstats.exception.GroupNotFoundException;
import de.niklasvoelker.beerpongstats.exception.InvitationInvalidException;
import de.niklasvoelker.beerpongstats.exception.UnknownUserException;
import de.niklasvoelker.beerpongstats.model.AppUser;
import de.niklasvoelker.beerpongstats.model.Invitation;
import de.niklasvoelker.beerpongstats.model.InvitationStatus;
import de.niklasvoelker.beerpongstats.model.PongGroup;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.model.Role;
import de.niklasvoelker.beerpongstats.repository.AppUserRepository;
import de.niklasvoelker.beerpongstats.repository.GroupRepository;
import de.niklasvoelker.beerpongstats.repository.InvitationRepository;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import de.niklasvoelker.beerpongstats.service.GroupService.GroupMembership;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class InvitationService {

    static final int DEFAULT_VALID_DAYS = 7;
    static final int MAX_VALID_DAYS = 30;
    static final int MAX_USES_LIMIT = 100;
    private static final int TOKEN_BYTES = 32;

    /** Eine neu erzeugte Einladung samt Token. Nur hier existiert der Token im Klartext. */
    public record CreatedInvitation(Invitation invitation, String token) {
    }

    /** Was jemand mit dem Link vor dem Beitreten sehen darf. */
    public record Preview(String groupName, long memberCount) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InvitationRepository invitationRepository;
    private final GroupRepository groupRepository;
    private final PlayerRepository playerRepository;
    private final AppUserRepository userRepository;
    private final GroupAccess groupAccess;
    private final GroupLimits limits;

    public InvitationService(InvitationRepository invitationRepository, GroupRepository groupRepository,
                             PlayerRepository playerRepository, AppUserRepository userRepository,
                             GroupAccess groupAccess, GroupLimits limits) {
        this.invitationRepository = invitationRepository;
        this.groupRepository = groupRepository;
        this.playerRepository = playerRepository;
        this.userRepository = userRepository;
        this.groupAccess = groupAccess;
        this.limits = limits;
    }

    @Transactional
    public CreatedInvitation createInvitation(Long groupId, Long userId, Integer validDays, Integer maxUses) {
        groupAccess.requireAdmin(groupId, userId);

        int days = validDays == null ? DEFAULT_VALID_DAYS : validDays;
        if (days < 1 || days > MAX_VALID_DAYS) {
            throw new IllegalArgumentException(
                    "Die Einladung muss zwischen 1 und " + MAX_VALID_DAYS + " Tage gültig sein.");
        }
        if (maxUses != null && (maxUses < 1 || maxUses > MAX_USES_LIMIT)) {
            throw new IllegalArgumentException(
                    "Die Zahl der Nutzungen muss zwischen 1 und " + MAX_USES_LIMIT + " liegen.");
        }

        String token = newToken();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Invitation invitation = invitationRepository.save(Invitation.builder()
                .group(groupRepository.getReferenceById(groupId))
                .tokenHash(hash(token))
                .createdAt(now)
                .expiresAt(now.plusDays(days))
                .maxUses(maxUses)
                .build());

        return new CreatedInvitation(invitation, token);
    }

    @Transactional(readOnly = true)
    public List<Invitation> getInvitations(Long groupId, Long userId) {
        groupAccess.requireAdmin(groupId, userId);
        return invitationRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
    }

    @Transactional
    public void revoke(Long groupId, Long userId, Long invitationId) {
        groupAccess.requireAdmin(groupId, userId);
        Invitation invitation = invitationRepository.findByIdAndGroupId(invitationId, groupId)
                .orElseThrow(GroupNotFoundException::new);
        if (invitation.getRevokedAt() == null) {
            invitation.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
    }

    @Transactional(readOnly = true)
    public Preview preview(String token) {
        Invitation invitation = findUsable(token);
        Long groupId = invitation.getGroup().getId();
        PongGroup group = groupRepository.findById(groupId).orElseThrow(InvitationInvalidException::new);
        return new Preview(group.getName(), playerRepository.countByGroupIdAndUserIsNotNull(groupId));
    }

    /**
     * Der Benutzer tritt der Gruppe bei. Die Gruppe wird dafür gesperrt, gleichzeitige Beitritte laufen also nacheinander
     * und können die Obergrenze nicht überschreiten. Die Nutzung der Einladung wird atomar vergeben; schlägt danach noch
     * etwas fehl, wird sie mit der Transaktion zurückgenommen.
     */
    @Transactional
    public GroupMembership accept(String token, Long userId, String displayName) {
        Invitation invitation = findUsable(token);
        AppUser user = userRepository.findById(userId).orElseThrow(UnknownUserException::new);
        Long groupId = invitation.getGroup().getId();
        PongGroup group = groupRepository.findForUpdate(groupId).orElseThrow(InvitationInvalidException::new);

        if (playerRepository.findByGroupIdAndUserId(groupId, userId).isPresent()) {
            throw new GroupConflictException("Du bist bereits Mitglied dieser Gruppe.");
        }
        String name = GroupService.resolveDisplayName(displayName, user);
        if (playerRepository.existsByGroupIdAndNameIgnoreCase(groupId, name)) {
            throw new GroupConflictException(
                    "Der Name '" + name + "' ist in dieser Gruppe schon vergeben. Wähle einen anderen.");
        }
        if (playerRepository.countByGroupIdAndUserIsNotNull(groupId) >= limits.maxMembersPerGroup()) {
            throw new GroupConflictException("Die Gruppe ist voll.");
        }
        GroupService.requireRoomForAnotherGroup(playerRepository, limits, userId);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (invitationRepository.consumeUse(invitation.getId(), now) == 0) {
            throw new InvitationInvalidException();
        }

        Player player;
        try {
            player = playerRepository.saveAndFlush(GroupService.newMember(group, user, name, Role.MEMBER, now));
        } catch (DataIntegrityViolationException e) {
            // Gleichzeitiger Beitritt desselben Accounts: einer der Unique-Indizes (Name oder Account) hat ihn abgelehnt
            throw new GroupConflictException(
                    "Der Beitritt hat sich mit einem anderen überschnitten. Versuche es noch einmal.");
        }

        return new GroupMembership(group, player, playerRepository.countByGroupIdAndUserIsNotNull(groupId));
    }

    private Invitation findUsable(String token) {
        Invitation invitation = invitationRepository.findByTokenHash(hash(token))
                .orElseThrow(InvitationInvalidException::new);
        if (invitation.statusAt(LocalDateTime.now(ZoneOffset.UTC)) != InvitationStatus.ACTIVE) {
            throw new InvitationInvalidException();
        }
        return invitation;
    }

    static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 ist auf jeder Java-Plattform vorhanden", e);
        }
    }
}
