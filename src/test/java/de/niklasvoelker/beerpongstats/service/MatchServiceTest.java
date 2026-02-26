package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.dto.MatchDTO;
import de.niklasvoelker.beerpongstats.exception.PlayerNotFoundByNameException;
import de.niklasvoelker.beerpongstats.model.Match;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.repository.MatchRepository;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private MatchService matchService;

    private Player playerA;
    private Player playerB;
    private Player playerC;
    private Player playerD;

    @BeforeEach
    void setUp() {
        playerA = new Player(); playerA.setName("A");
        playerB = new Player(); playerB.setName("B");
        playerC = new Player(); playerC.setName("C");
        playerD = new Player(); playerD.setName("D");
    }

    @Test
    void createMatch_shouldSave1v1MatchAndUpdateStats() {
        MatchDTO dto = new MatchDTO();
        dto.setWinner1("A");
        dto.setLoser1("B");
        dto.setWinner1Cups(3);
        dto.setLoser1Cups(1);

        when(playerRepository.findByName("A")).thenReturn(Optional.of(playerA));
        when(playerRepository.findByName("B")).thenReturn(Optional.of(playerB));

        matchService.createMatch(dto);

        // Prüfen, dass Match gespeichert wurde
        ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(captor.capture());
        Match savedMatch = captor.getValue();

        assertEquals(playerA, savedMatch.getWinner1());
        assertEquals(playerB, savedMatch.getLoser1());
        assertNull(savedMatch.getWinner2());
        assertNull(savedMatch.getLoser2());

        assertEquals(1, playerA.getWins());
        assertEquals(0, playerA.getLosses());
        assertEquals(3, playerA.getAvgCups());
        assertEquals(1, playerA.getWinRate());

        assertEquals(0, playerB.getWins());
        assertEquals(1, playerB.getLosses());
        assertEquals(1, playerB.getAvgCups());
        assertEquals(0, playerB.getWinRate());
    }

    @Test
    void createMatch_shouldSave2v2MatchAndUpdateStats() {
        MatchDTO dto = new MatchDTO();
        dto.setWinner1("A");
        dto.setWinner2("B");
        dto.setLoser1("C");
        dto.setLoser2("D");
        dto.setWinner1Cups(3);
        dto.setWinner2Cups(2);
        dto.setLoser1Cups(1);
        dto.setLoser2Cups(0);

        when(playerRepository.findByName("A")).thenReturn(Optional.of(playerA));
        when(playerRepository.findByName("B")).thenReturn(Optional.of(playerB));
        when(playerRepository.findByName("C")).thenReturn(Optional.of(playerC));
        when(playerRepository.findByName("D")).thenReturn(Optional.of(playerD));

        matchService.createMatch(dto);

        ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(captor.capture());
        Match saved = captor.getValue();

        // Winner prüfen
        assertEquals(playerA, saved.getWinner1());
        assertEquals(playerB, saved.getWinner2());
        // Loser prüfen
        assertEquals(playerC, saved.getLoser1());
        assertEquals(playerD, saved.getLoser2());

        // Stats prüfen
        assertEquals(1, playerA.getWins());
        assertEquals(3, playerA.getAvgCups());
        assertEquals(1, playerB.getWins());
        assertEquals(2, playerB.getAvgCups());

        assertEquals(1, playerC.getLosses());
        assertEquals(1, playerC.getAvgCups());
        assertEquals(1, playerD.getLosses());
        assertEquals(0, playerD.getAvgCups());
    }

    @Test
    void createMatch_shouldThrowIfWinnerNotFound() {
        MatchDTO dto = new MatchDTO();
        dto.setWinner1("A");
        dto.setLoser1("B");
        dto.setWinner1Cups(3);
        dto.setLoser1Cups(1);

        when(playerRepository.findByName("A")).thenReturn(Optional.empty());

        assertThrows(PlayerNotFoundByNameException.class,
                () -> matchService.createMatch(dto));
    }

    @Test
    void createMatch_shouldThrowIfSamePlayerInMatch() {
        MatchDTO dto = new MatchDTO();
        dto.setWinner1("A");
        dto.setLoser1("A"); // gleiche Person

        assertThrows(IllegalArgumentException.class,
                () -> matchService.createMatch(dto));
    }

    @Test
    void createMatch_shouldThrowIfCupsMissingIn2v2() {
        MatchDTO dto = new MatchDTO();
        dto.setWinner1("A");
        dto.setWinner2("B");
        dto.setLoser1("C");
        dto.setLoser2("D");

        dto.setWinner1Cups(null);
        dto.setWinner2Cups(2);
        dto.setLoser1Cups(1);
        dto.setLoser2Cups(0);

        assertThrows(IllegalArgumentException.class,
                () -> matchService.createMatch(dto));
    }
}
