package cl.kynex.identity.config;

import cl.kynex.identity.repository.UserRepository;
import cl.kynex.identity.repository.RefreshSessionRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.web.cors.*;

/** JWT para la API; cookie HttpOnly y CSRF para renovar la sesión del navegador. */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean JwtEncoder jwtEncoder(SecurityProperties config) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(config.jwtSecret().getBytes(StandardCharsets.UTF_8)));
    }

    @Bean JwtDecoder jwtDecoder(SecurityProperties config) {
        var key = new SecretKeySpec(config.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var audience = new JwtClaimValidator<List<String>>("aud", values -> values != null && values.contains("kynex-web"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(config.issuer()), audience));
        return decoder;
    }

    @Bean CorsConfigurationSource corsConfigurationSource(SecurityProperties config) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(config.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        cors.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean SecurityFilterChain security(HttpSecurity http, UserRepository users, RefreshSessionRepository sessions, SecurityProperties config) throws Exception {
        var csrfRepository = new CookieCsrfTokenRepository();
        csrfRepository.setCookieCustomizer(cookie -> cookie.httpOnly(true).secure(config.cookieSecure()).sameSite("Strict"));
        http.cors(cors -> {}).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            // Solo estas rutas aceptan la credencial ambiental (cookie) del navegador.
            .requireCsrfProtectionMatcher(request -> !List.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())
                && request.getRequestURI().startsWith(request.getContextPath() + "/api/v1/auth/")));
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/auth/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/actuator/health").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/products/**", "/api/v1/categories/**", "/api/v1/exercises/**", "/media/products/**", "/media/training/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/contact").permitAll()
            .anyRequest().authenticated());
        http.oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
            // Reconsulta estado y rol: editar un JWT o una sesión local no concede permisos.
            Long id;
            try { id = Long.valueOf(token.getSubject()); }
            catch (RuntimeException ex) { throw invalidToken(); }
            var user = users.findById(id).filter(value -> value.active).orElseThrow(SecurityConfig::invalidToken);
            try {
                var family = java.util.UUID.fromString(token.getClaimAsString("sid"));
                if (!sessions.existsByFamilyAndRevokedFalseAndExpiresAtAfter(family, java.time.Instant.now())) throw invalidToken();
            } catch (IllegalArgumentException | NullPointerException ex) { throw invalidToken(); }
            return new UsernamePasswordAuthenticationToken(user.id.toString(), token,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role.name())));
        })));
        return http.build();
    }

    private static OAuth2AuthenticationException invalidToken() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
    }
}
