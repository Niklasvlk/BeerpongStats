package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.config.GroupAccess;
import de.niklasvoelker.beerpongstats.config.GroupLimits;
import de.niklasvoelker.beerpongstats.exception.GroupConflictException;
import de.niklasvoelker.beerpongstats.exception.GroupNotFoundException;
import de.niklasvoelker.beerpongstats.exception.PlayerNotFoundByIDException;
import de.niklasvoelker.beerpongstats.exception.UnknownUserException;
import de.niklasvoelker.beerpongstats.model.AppUser;
import de.niklasvoelker.beerpongstats.model.PongGroup;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.model.Role;
import de.niklasvoelker.beerpongstats.repository.AppUserRepository;
import de.niklasvoelker.beerpongstats.repository.GroupRepository;
import de.niklasvoelker.beerpongstats.repository.InvitationRepository;
import de.niklasvoelker.beerpongstats.repository.MatchRepository;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class GroupService {

    static final int MAX_GROUP_NAME_LENGTH = 50;
    static final int MAX_DISPLAY_NAME_LENGTH = UserService.MAX_USERNAME_LENGTH;

    /**
     * Eine Gruppe aus Sicht eines Mitglieds: die Gruppe, sein Spieler darin und die Zahl der Mitglieder (Spieler mit
     * Account).
     */
    public record GroupMembership(PongGroup group, Player player, long memberCount) {
    }

    private final GroupRepository groupRepository;
    private final PlayerRepository playerRepository;
    private final AppUserRepository userRepository;
    private final MatchRepository matchRepository;
    private final InvitationRepository invitationRepository;
    private final GroupAccess groupAccess;
    private final GroupLimits limits;

    public GroupService(GroupRepository groupRepository, PlayerRepository playerRepository,
                        AppUserRepository userRepository, MatchRepository matchRepository,
                        InvitationRepository invitationRepository, GroupAccess groupAccess, GroupLimits limits) {
        this.groupRepository = groupRepository;
        this.playerRepository = playerRepository;
        this.userRepository = userRepository;
        this.matchRepository = matchRepository;
        this.invitationRepository = invitationRepository;
        this.groupAccess = groupAccess;
        this.limits = limits;
    }

    // Der Ersteller wird Admin; sein Spielername in der Gruppe ist standardmäßig der Benutzername
    @Transactional
    public GroupMembership createGroup(Long userId, String name, String displayName) {
        AppUser user = userRepository.findById(userId).orElseThrow(UnknownUserException::new);
        requireRoomForAnotherGroup(playerRepository, limits, userId);

        String groupName = requireText(name, "Der Gruppenname darf nicht leer sein.",
                "Der Gruppenname darf höchstens " + MAX_GROUP_NAME_LENGTH + " Zeichen lang sein.", MAX_GROUP_NAME_LENGTH);
        String playerName = resolveDisplayName(displayName, user);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        PongGroup group = groupRepository.save(PongGroup.builder().name(groupName).createdAt(now).build());
        Player player = playerRepository.save(newMember(group, user, playerName, Role.ADMIN, now));

        return new GroupMembership(group, player, 1);
    }

    @Transactional
    public GroupMembership renameGroup(Long groupId, Long userId, String name) {
        Player admin = groupAccess.requireAdmin(groupId, userId);
        PongGroup group = groupRepository.findById(groupId).orElseThrow(GroupNotFoundException::new);

        group.setName(requireText(name, "Der Gruppenname darf nicht leer sein.",
                "Der Gruppenname darf höchstens " + MAX_GROUP_NAME_LENGTH + " Zeichen lang sein.", MAX_GROUP_NAME_LENGTH));

        return new GroupMembership(group, admin, playerRepository.countByGroupIdAndUserIsNotNull(groupId));
    }

    // Löscht die Gruppe samt Matches, Spielern und Einladungen
    @Transactional
    public void deleteGroup(Long groupId, Long userId) {
        groupAccess.requireAdmin(groupId, userId);
        deleteGroupData(groupId);
    }

    @Transactional(readOnly = true)
    public List<GroupMembership> getMyGroups(Long userId) {
        return playerRepository.findMemberships(userId).stream()
                .map(player -> new GroupMembership(player.getGroup(), player,
                        playerRepository.countByGroupIdAndUserIsNotNull(player.getGroup().getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupMembership getGroup(Long groupId, Long userId) {
        Player player = playerRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(GroupNotFoundException::new);
        PongGroup group = groupRepository.findById(groupId).orElseThrow(GroupNotFoundException::new);
        return new GroupMembership(group, player, playerRepository.countByGroupIdAndUserIsNotNull(groupId));
    }

    // Alle Spieler der Gruppe, auch die ohne Account (erkennbar an user == null)
    @Transactional(readOnly = true)
    public List<Player> getPlayers(Long groupId) {
        return playerRepository.findByGroupId(groupId, Sort.by(Sort.Direction.ASC, "name"));
    }

    @Transactional
    public Player changeRole(Long groupId, Long userId, Long playerId, Role role) {
        groupAccess.requireAdmin(groupId, userId);
        if (role == null) {
            throw new IllegalArgumentException("Die Rolle muss angegeben werden.");
        }

        Player target = playerRepository.findByIdAndGroupId(playerId, groupId)
                .orElseThrow(() -> new PlayerNotFoundByIDException(playerId));
        if (target.getUser() == null) {
            throw new GroupConflictException("Spieler ohne Account können keine Rolle bekommen.");
        }
        if (target.getRole() == Role.ADMIN && role != Role.ADMIN && isLastAdmin(target)) {
            throw new GroupConflictException("Die Gruppe braucht mindestens einen Admin.");
        }

        target.setRole(role);
        return target;
    }

    /**
     * Ein Admin entfernt ein Mitglied. Wie beim Verlassen bleibt dessen Spieler ohne Account in der Gruppe stehen.
     * Admins müssen vorher herabgestuft werden, damit niemand einen anderen Admin mit einem Klick hinauswirft.
     */
    @Transactional
    public void removeMember(Long groupId, Long userId, Long playerId) {
        Player admin = groupAccess.requireAdmin(groupId, userId);

        Player target = playerRepository.findByIdAndGroupId(playerId, groupId)
                .orElseThrow(() -> new PlayerNotFoundByIDException(playerId));
        if (target.getId().equals(admin.getId())) {
            throw new GroupConflictException("Um die Gruppe zu verlassen, nutze 'Gruppe verlassen'.");
        }
        if (target.getUser() == null) {
            throw new GroupConflictException("Dieser Spieler gehört schon zu keinem Account mehr.");
        }
        if (target.getRole() == Role.ADMIN) {
            throw new GroupConflictException("Admins können nicht entfernt werden. Stufe sie zuerst zum Mitglied herab.");
        }

        detach(target);
    }

    /**
     * Der Benutzer verlässt die Gruppe. Sein Spieler bleibt mit Name und Statistik erhalten, damit die Matches lesbar
     * bleiben, gehört aber zu keinem Account mehr. War er das letzte Mitglied, wird die Gruppe gelöscht.
     */
    @Transactional
    public void leaveGroup(Long groupId, Long userId) {
        Player me = playerRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(GroupNotFoundException::new);
        releaseMembership(me);
    }

    /**
     * Löst alle Mitgliedschaften eines Accounts, der gelöscht wird. Vorher wird für jede Gruppe geprüft, ob das
     * möglich ist, damit nichts halb passiert.
     */
    @Transactional
    public void releaseMemberships(Long userId) {
        List<Player> memberships = playerRepository.findMemberships(userId);
        memberships.forEach(this::requireCanLeave);

        // Erst entscheiden, dann ändern: Das Löschen einer Gruppe verändert die Mitgliederzahlen der anderen nicht,
        // aber so hängt das Ergebnis nicht von der Reihenfolge ab
        List<Player> lastMembers = memberships.stream().filter(this::isOnlyMember).toList();
        memberships.stream().filter(member -> !lastMembers.contains(member)).forEach(this::detach);
        lastMembers.forEach(member -> deleteGroupData(member.getGroup().getId()));
    }

    private void releaseMembership(Player member) {
        requireCanLeave(member);
        if (isOnlyMember(member)) {
            deleteGroupData(member.getGroup().getId());
        } else {
            detach(member);
        }
    }

    private void detach(Player member) {
        member.setUser(null);
        member.setRole(Role.MEMBER);
    }

    private void requireCanLeave(Player member) {
        if (member.getRole() == Role.ADMIN && !isOnlyMember(member) && isLastAdmin(member)) {
            throw new GroupConflictException("In der Gruppe '" + member.getGroup().getName()
                    + "' bist du der letzte Admin. Mache zuerst jemand anderen zum Admin.");
        }
    }

    private boolean isOnlyMember(Player member) {
        return playerRepository.countByGroupIdAndUserIsNotNull(member.getGroup().getId()) <= 1;
    }

    private boolean isLastAdmin(Player admin) {
        return playerRepository.countByGroupIdAndRoleAndUserIsNotNull(admin.getGroup().getId(), Role.ADMIN) <= 1;
    }

    // Reihenfolge wegen der Fremdschlüssel: Matches, Einladungen, Spieler, dann die Gruppe
    private void deleteGroupData(Long groupId) {
        matchRepository.deleteAllOfGroup(groupId);
        invitationRepository.deleteAllOfGroup(groupId);
        playerRepository.deleteAllOfGroup(groupId);
        groupRepository.deleteGroup(groupId);
    }

    static void requireRoomForAnotherGroup(PlayerRepository playerRepository, GroupLimits limits, Long userId) {
        if (playerRepository.countByUserId(userId) >= limits.maxGroupsPerUser()) {
            throw new GroupConflictException(
                    "Du kannst höchstens in " + limits.maxGroupsPerUser() + " Gruppen Mitglied sein.");
        }
    }

    static Player newMember(PongGroup group, AppUser user, String name, Role role, LocalDateTime now) {
        return Player.builder()
                .group(group)
                .user(user)
                .name(name)
                .role(role)
                .wins(0)
                .losses(0)
                .avgCups(0)
                .winRate(0)
                .createdAt(now)
                .build();
    }

    // Ohne Angabe heißt man in der Gruppe wie der Benutzername
    static String resolveDisplayName(String displayName, AppUser user) {
        if (displayName == null || displayName.isBlank()) {
            return user.getUsername();
        }
        return requireText(displayName, "Der Spielername darf nicht leer sein.",
                "Der Spielername darf höchstens " + MAX_DISPLAY_NAME_LENGTH + " Zeichen lang sein.",
                MAX_DISPLAY_NAME_LENGTH);
    }

    static String requireText(String value, String emptyMessage, String tooLongMessage, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(tooLongMessage);
        }
        return value.trim();
    }
}
