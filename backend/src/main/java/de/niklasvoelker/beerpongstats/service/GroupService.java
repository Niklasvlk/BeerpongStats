package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.exception.GroupNotFoundException;
import de.niklasvoelker.beerpongstats.exception.UnknownUserException;
import de.niklasvoelker.beerpongstats.model.AppUser;
import de.niklasvoelker.beerpongstats.model.PongGroup;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.model.Role;
import de.niklasvoelker.beerpongstats.repository.AppUserRepository;
import de.niklasvoelker.beerpongstats.repository.GroupRepository;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class GroupService {

    static final int MAX_GROUP_NAME_LENGTH = 50;
    static final int MAX_DISPLAY_NAME_LENGTH = UserService.MAX_USERNAME_LENGTH;

    /** Eine Gruppe aus Sicht eines Mitglieds: die Gruppe, sein Spieler darin und die Zahl der Mitglieder. */
    public record GroupMembership(PongGroup group, Player player, long memberCount) {
    }

    private final GroupRepository groupRepository;
    private final PlayerRepository playerRepository;
    private final AppUserRepository userRepository;

    public GroupService(GroupRepository groupRepository, PlayerRepository playerRepository,
                        AppUserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.playerRepository = playerRepository;
        this.userRepository = userRepository;
    }

    // Der Ersteller wird Admin; sein Spielername in der Gruppe ist standardmäßig der Benutzername
    @Transactional
    public GroupMembership createGroup(Long userId, String name, String displayName) {
        AppUser user = userRepository.findById(userId).orElseThrow(UnknownUserException::new);

        String groupName = requireText(name, "Der Gruppenname darf nicht leer sein.",
                "Der Gruppenname darf höchstens " + MAX_GROUP_NAME_LENGTH + " Zeichen lang sein.", MAX_GROUP_NAME_LENGTH);
        String playerName = displayName == null || displayName.isBlank()
                ? user.getUsername()
                : requireText(displayName, "Der Spielername darf nicht leer sein.",
                "Der Spielername darf höchstens " + MAX_DISPLAY_NAME_LENGTH + " Zeichen lang sein.",
                MAX_DISPLAY_NAME_LENGTH);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        PongGroup group = groupRepository.save(PongGroup.builder().name(groupName).createdAt(now).build());
        Player player = playerRepository.save(Player.builder()
                .group(group)
                .user(user)
                .name(playerName)
                .role(Role.ADMIN)
                .wins(0)
                .losses(0)
                .avgCups(0)
                .winRate(0)
                .createdAt(now)
                .build());

        return new GroupMembership(group, player, 1);
    }

    @Transactional(readOnly = true)
    public List<GroupMembership> getMyGroups(Long userId) {
        return playerRepository.findMemberships(userId).stream()
                .map(player -> new GroupMembership(player.getGroup(), player,
                        playerRepository.countByGroupId(player.getGroup().getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupMembership getGroup(Long groupId, Long userId) {
        Player player = playerRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(GroupNotFoundException::new);
        PongGroup group = groupRepository.findById(groupId).orElseThrow(GroupNotFoundException::new);
        return new GroupMembership(group, player, playerRepository.countByGroupId(groupId));
    }

    private static String requireText(String value, String emptyMessage, String tooLongMessage, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(tooLongMessage);
        }
        return value.trim();
    }
}
