package de.niklasvoelker.beerpongstats.controller;

import de.niklasvoelker.beerpongstats.config.SecurityConfig;
import de.niklasvoelker.beerpongstats.model.Player;
import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
@Import(SecurityConfig.class)
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerRepository playerRepository;

    private Player player() {
        return Player.builder().id(1L).name("Max").password("x").build();
    }

    @Test
    void profile_shouldRedirectToLoginWhenAnonymous() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile/login"));
    }

    @Test
    @WithMockUser(username = "Max")
    void profile_shouldShowOwnProfile() throws Exception {
        when(playerRepository.findByName("Max")).thenReturn(Optional.of(player()));

        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk());
    }

    @Test
    void delete_shouldRejectAnonymous() throws Exception {
        mockMvc.perform(post("/profile/delete/1").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile/login"));

        verify(playerRepository, never()).deleteById(1L);
    }

    @Test
    @WithMockUser(username = "Max")
    void delete_shouldRejectForeignId() throws Exception {
        when(playerRepository.findByName("Max")).thenReturn(Optional.of(player()));

        mockMvc.perform(post("/profile/delete/2").with(csrf()))
                .andExpect(status().isForbidden());

        verify(playerRepository, never()).deleteById(2L);
    }

    @Test
    @WithMockUser(username = "Max")
    void delete_shouldDeleteOwnAccount() throws Exception {
        when(playerRepository.findByName("Max")).thenReturn(Optional.of(player()));

        mockMvc.perform(post("/profile/delete/1").with(csrf()))
                .andExpect(redirectedUrl("/"));

        verify(playerRepository).deleteById(1L);
    }
}
