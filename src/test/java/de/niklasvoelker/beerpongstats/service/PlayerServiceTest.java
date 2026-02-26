package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.exception.InvalidPlayerDataException;
import de.niklasvoelker.beerpongstats.exception.PlayerAlreadyExistsException;
import de.niklasvoelker.beerpongstats.exception.PlayerNotFoundByIDException;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
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
class PlayerServiceTest {

    @Mock
    private PlayerRepository repository;

    @InjectMocks
    private PlayerService playerService;

    @Test
    void createPlayer_shouldSavePlayerCorrectly() {
        when(repository.existsByNameIgnoreCase("Max")).thenReturn(false);

        playerService.createPlayer("Max", "1234");

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(repository).save(captor.capture());

        Player saved = captor.getValue();
        assertEquals("Max", saved.getName());
        assertEquals("1234", saved.getPassword());
        assertEquals(0, saved.getWins());
        assertEquals(0, saved.getLosses());
        assertEquals(0, saved.getAvgCups());
        assertEquals(0, saved.getWinRate());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void createPlayer_shouldThrowIfNameEmpty() {
        InvalidPlayerDataException ex = assertThrows(
                InvalidPlayerDataException.class,
                () -> playerService.createPlayer("   ", "1234")
        );
        assertEquals("Der Spielername darf nicht leer sein.", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createPlayer_shouldThrowIfNameNull() {
        InvalidPlayerDataException ex = assertThrows(
                InvalidPlayerDataException.class,
                () -> playerService.createPlayer(null, "1234")
        );
        assertEquals("Der Spielername darf nicht leer sein.", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createPlayer_shouldThrowIfPasswordTooShort() {
        InvalidPlayerDataException ex = assertThrows(
                InvalidPlayerDataException.class,
                () -> playerService.createPlayer("Max", "123")
        );
        assertEquals("Das Passwort ist zu kurz.", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createPlayer_shouldThrowIfPasswordNull() {
        InvalidPlayerDataException ex = assertThrows(
                InvalidPlayerDataException.class,
                () -> playerService.createPlayer("Max", null)
        );
        assertEquals("Das Passwort ist zu kurz.", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void createPlayer_shouldThrowIfPlayerExists() {
        when(repository.existsByNameIgnoreCase("Max")).thenReturn(true);

        PlayerAlreadyExistsException ex = assertThrows(
                PlayerAlreadyExistsException.class,
                () -> playerService.createPlayer("Max", "1234")
        );
        assertTrue(ex.getMessage().contains("Max"));
        verify(repository, never()).save(any());
    }

    @Test
    void getPlayerById_shouldReturnPlayerIfExists() {
        Player player = new Player();
        player.setId(1L);
        player.setName("Max");

        when(repository.findById(1L)).thenReturn(Optional.of(player));

        Player result = playerService.getPlayerById(1L);

        assertEquals("Max", result.getName());
        verify(repository).findById(1L);
    }

    @Test
    void getPlayerById_shouldThrowIfNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(PlayerNotFoundByIDException.class,
                () -> playerService.getPlayerById(1L));

        verify(repository).findById(1L);
    }
}
