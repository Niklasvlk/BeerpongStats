package de.niklasvoelker.beerpongstats.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Gewinner-Team
    @ManyToOne
    @JoinColumn(name = "winner1_id", nullable = false)
    private Player winner1;

    @ManyToOne
    @JoinColumn(name = "winner2_id")
    private Player winner2; // optional

    // Verlierer-Team
    @ManyToOne
    @JoinColumn(name = "loser1_id", nullable = false)
    private Player loser1;

    @ManyToOne
    @JoinColumn(name = "loser2_id")
    private Player loser2; // optional

    // Optional: getroffene Becher pro Spieler
    private Integer winner1Cups; // nullable = optional
    private Integer winner2Cups;
    private Integer loser1Cups;
    private Integer loser2Cups;

    private LocalDateTime playedAt;
}