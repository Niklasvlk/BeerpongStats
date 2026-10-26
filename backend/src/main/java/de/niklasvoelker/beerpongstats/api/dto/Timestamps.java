package de.niklasvoelker.beerpongstats.api.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

final class Timestamps {

    private Timestamps() {
    }

    // Die Datenbank speichert Zeiten als UTC-LocalDateTime; mit Offset ausgeliefert zeigt der Browser die richtige Ortszeit
    static Instant toInstant(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }
}
