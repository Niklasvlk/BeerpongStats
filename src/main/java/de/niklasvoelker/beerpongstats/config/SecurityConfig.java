package de.niklasvoelker.beerpongstats.config;

import de.niklasvoelker.beerpongstats.repository.PlayerRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PlayerRepository playerRepository) {
        return username -> playerRepository.findByName(username)
                .map(player -> User.withUsername(player.getName())
                        .password(player.getPassword())
                        .roles("PLAYER")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/profile", "/profile/delete/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/profile/login")
                        .loginProcessingUrl("/profile/login")
                        .usernameParameter("name")
                        .defaultSuccessUrl("/profile", true)
                        .failureUrl("/profile/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/profile/logout")
                        .logoutSuccessUrl("/"));
        return http.build();
    }
}
