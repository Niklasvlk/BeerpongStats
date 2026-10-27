package de.niklasvoelker.beerpongstats.api;

import de.niklasvoelker.beerpongstats.api.dto.LoginRequest;
import de.niklasvoelker.beerpongstats.api.dto.RegisterRequest;
import de.niklasvoelker.beerpongstats.api.dto.UserResponse;
import de.niklasvoelker.beerpongstats.config.AppUserDetails;
import de.niklasvoelker.beerpongstats.exception.TooManyRequestsException;
import de.niklasvoelker.beerpongstats.model.AppUser;
import de.niklasvoelker.beerpongstats.ratelimit.RateLimiters;
import de.niklasvoelker.beerpongstats.ratelimit.SlidingWindowLimiter;
import de.niklasvoelker.beerpongstats.repository.AppUserRepository;
import de.niklasvoelker.beerpongstats.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository userRepository;
    private final UserService userService;
    private final CsrfTokenRepository csrfTokenRepository;
    private final RateLimiters rateLimiters;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthApiController(AuthenticationManager authenticationManager, AppUserRepository userRepository,
                             UserService userService, CsrfTokenRepository csrfTokenRepository,
                             RateLimiters rateLimiters) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.userService = userService;
        this.csrfTokenRepository = csrfTokenRepository;
        this.rateLimiters = rateLimiters;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
        AppUser user = userService.register(request.username(), request.password());
        return ResponseEntity.created(URI.create("/api/auth/me")).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public UserResponse login(@RequestBody LoginRequest login,
                              HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticate(login);

        // Session-Fixation-Schutz
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        return currentUser(authentication);
    }

    // Zusätzlich zur Grenze pro Client-Adresse (RateLimitFilter): Wer sich als ein bestimmter Benutzer anmelden will,
    // hat nur wenige Fehlversuche, auch wenn sie von vielen Adressen kommen. Gesperrt wird, ohne das Passwort zu prüfen,
    // sonst ließe es sich trotz Sperre weiter erraten. Unbekannte Namen zählen genauso wie bekannte, die Sperre
    // verrät also nicht, ob es einen Account gibt.
    private Authentication authenticate(LoginRequest login) {
        SlidingWindowLimiter failures = rateLimiters.failedLogins();
        String key = login.username() == null ? "" : login.username().trim().toLowerCase(Locale.ROOT);
        key = key.length() > 100 ? key.substring(0, 100) : key;

        if (rateLimiters.enabled() && failures.isLimited(key)) {
            throw new TooManyRequestsException(failures.retryAfterSeconds(key));
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(login.username(), login.password()));
            failures.reset(key);
            return authentication;
        } catch (AuthenticationException e) {
            if (rateLimiters.enabled()) {
                failures.record(key);
            }
            throw e;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) throws ServletException {
        endSession(request, response);
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return currentUser(authentication);
    }

    // Löscht den eigenen Account
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(Authentication authentication, HttpServletRequest request, HttpServletResponse response)
            throws ServletException {
        userService.deleteUser(((AppUserDetails) authentication.getPrincipal()).getId());
        // Die Session gehört zum gelöschten Benutzer und darf nicht weiterleben
        endSession(request, response);
    }

    // Beim Abmelden löscht Spring Security auch das CSRF-Cookie. Ohne ein neues scheitert die nächste POST-Anfrage, etwa
    // der Login gleich danach, mit 403.
    private void endSession(HttpServletRequest request, HttpServletResponse response) throws ServletException {
        request.logout();
        csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
    }

    private UserResponse currentUser(Authentication authentication) {
        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        // Existiert der Benutzer nicht mehr (Account gelöscht), ist die Session ungültig
        AppUser user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return UserResponse.from(user);
    }
}
