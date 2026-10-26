package de.niklasvoelker.beerpongstats.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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
    private String password;

    private int wins;
    private int losses;
    private double winRate;
    private double avgCups;
    private LocalDateTime createdAt;
}


