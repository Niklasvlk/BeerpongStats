package de.niklasvoelker.beerpongstats.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchDTO {
    private String winner1;
    private String winner2; // optional
    private String loser1;
    private String loser2; // optional
    private Integer winner1Cups;
    private Integer winner2Cups;
    private Integer loser1Cups;
    private Integer loser2Cups;
}