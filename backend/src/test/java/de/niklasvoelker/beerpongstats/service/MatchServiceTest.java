package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.dto.MatchDTO;
import de.niklasvoelker.beerpongstats.exception.PlayerNotFoundByNameException;
import de.niklasvoelker.beerpongstats.model.Match;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.model.PongGroup;
import de.niklasvoelker.beerpongstats.repository.GroupRepository;
import de.niklasvoelker.beerpongstats.repository.MatchRepository;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private MatchService matchService;

    private static final Long GROUP_ID = 1L;

    private Player playerA;
    private Player playerB;
    private Player playerC;
    private Player playerD;
    private PongGroup group;

    @BeforeEach
    void setUp() {
        group = PongGroup.builder().id(GROUP_ID).name("Kumpels").build();
        lenient().when(groupRepository.getReferenceById(GROUP_ID)).thenReturn(group);
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

        when(playerRepository.findByGroupIdAndNameIgnoreCase(GROUP_ID, "A")).thenReturn(Optional.of(playerA));
        when(playerRepository.findByGroupIdAndNameIgnoreCase(GROUP_ID, "B")).thenReturn(Optional.of(playerB));

        matchService.createMatch(GROUP_ID, dto);

        // Prüfen, dass Match gespeichert wurde
        ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(captor.capture());
        Match savedMatch = captor.getValue();

        assertEquals(playerA, savedMatch.getWinner1());
        assertEquals(playerB, savedMatch.getLoser1());
        assertSame(group, savedMatch.getGroup());
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

        when(playerRepository.findByGroupIdAndNameIgnoreCase(GROUP_ID, "A")).thenReturn(Optional.of(playerA));
        when(playerRepository.findByGroupIdAndNameIgnoreCase(GROUP_ID, "B")).thenReturn(Optional.of(playerB));
        when(playerRepository.findByGroupIdAndNameIgnoreCase(GROUP_ID, "C")).thenReturn(Optional.of(playerC));
        when(playerRepository.findByGroupIdAndNameIgnoreCase(GROUP_ID, "D")).thenReturn(Optional.of(playerD));

        matchService.createMatch(GROUP_ID, dto);

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

        when(playerRepository.findByGroupIdAndNameIgnoreCase(GROUP_ID, "A")).thenReturn(Optional.empty());

        assertThrows(PlayerNotFoundByNameException.class,
                () -> matchService.createMatch(GROUP_ID, dto));
    }

    @Test
    void createMatch_shouldThrowIfSamePlayerInMatch() {
        MatchDTO dto = new MatchDTO();
        dto.setWinner1("A");
        dto.setLoser1("A"); // gleiche Person

        assertThrows(IllegalArgumentException.class,
                () -> matchService.createMatch(GROUP_ID, dto));
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
                () -> matchService.createMatch(GROUP_ID, dto));
    }
}
