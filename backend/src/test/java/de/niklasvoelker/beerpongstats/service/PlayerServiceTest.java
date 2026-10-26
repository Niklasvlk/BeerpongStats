package de.niklasvoelker.beerpongstats.service;

import de.niklasvoelker.beerpongstats.exception.PlayerNotFoundByIDException;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
