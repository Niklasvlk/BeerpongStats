package de.niklasvoelker.beerpongstats.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Heißt nicht Group, weil das ein Schlüsselwort in HQL und SQL ist
@Entity
@Table(name = "pong_group")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PongGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private LocalDateTime createdAt;
}
