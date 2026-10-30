package de.niklasvoelker.beerpongstats.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Ein Einladungslink in eine Gruppe. Gespeichert wird nur der Hash des Tokens. */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private PongGroup group;

    private String tokenHash;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Integer maxUses; // null = unbegrenzt
    private int uses;
    private LocalDateTime revokedAt;

    public InvitationStatus statusAt(LocalDateTime now) {
        if (revokedAt != null) {
            return InvitationStatus.REVOKED;
        }
        if (!expiresAt.isAfter(now)) {
            return InvitationStatus.EXPIRED;
        }
        if (maxUses != null && uses >= maxUses) {
            return InvitationStatus.EXHAUSTED;
        }
        return InvitationStatus.ACTIVE;
    }
}
