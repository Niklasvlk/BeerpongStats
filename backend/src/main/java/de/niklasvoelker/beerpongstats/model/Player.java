package de.niklasvoelker.beerpongstats.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Ein Spieler in genau einer Gruppe (Mitgliedschaft samt Statistik). Dieselbe Person hat in jeder ihrer Gruppen
 * einen eigenen Player; ohne {@code user} gehört er zu keinem Account mehr.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Player {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    // Lazy, damit equals/hashCode/toString nie einen Proxy außerhalb der Transaktion anfassen
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private PongGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AppUser user;

    @Enumerated(EnumType.STRING)
    private Role role;

    private int wins;
    private int losses;
    private double winRate;
    private double avgCups;
    private LocalDateTime createdAt;
}
