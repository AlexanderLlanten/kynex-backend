package cl.kynex.identity.controller;

import cl.kynex.identity.config.SecurityProperties;
import cl.kynex.identity.dto.AuthDtos.*;
import cl.kynex.identity.service.AuthService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final SecurityProperties config;
    public AuthController(AuthService auth, SecurityProperties config) { this.auth = auth; this.config = config; }

    @GetMapping("/csrf") public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken(), "headerName", token.getHeaderName()); }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@Valid @RequestBody Register data) { return auth.register(data); }
    @PostMapping("/login") public ResponseEntity<Session> login(@Valid @RequestBody Login data) { return session(auth.login(data)); }
    @PostMapping("/refresh") public ResponseEntity<Session> refresh(@CookieValue(name="kynex_refresh", required=false) String token) { return session(auth.refresh(token)); }
    @GetMapping("/me") @PreAuthorize("isAuthenticated()") public UserView me(Principal principal) { return auth.identity(Long.valueOf(principal.getName())); }
    @PostMapping("/logout") public ResponseEntity<Void> logout(@CookieValue(name="kynex_refresh", required=false) String token) {
        auth.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }
    private ResponseEntity<Session> session(AuthService.Issued issued) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE, cookie(issued.refreshToken(), config.refreshDuration()).toString()).body(issued.body());
    }
    private ResponseCookie cookie(String token, Duration age) {
        return ResponseCookie.from("kynex_refresh", token).httpOnly(true).secure(config.cookieSecure())
            .sameSite("Strict").path("/api/v1/auth").maxAge(age).build();
    }
}
