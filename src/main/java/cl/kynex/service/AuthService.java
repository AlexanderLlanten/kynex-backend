package cl.kynex.service;

import cl.kynex.config.SecurityProperties;
import cl.kynex.dto.AuthDtos.*;
import cl.kynex.entity.*;
import cl.kynex.exception.ApiException;
import cl.kynex.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registro, contraseña, JWT breve y renovación rotativa revocable. */
@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final SecurityProperties config;
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshSessionRepository sessions, PasswordEncoder passwords,
            JwtEncoder encoder, SecurityProperties config) {
        this.users = users; this.sessions = sessions; this.passwords = passwords; this.encoder = encoder; this.config = config;
        dummyHash = passwords.encode(UUID.randomUUID().toString());
    }
    public record Issued(Session body, String refreshToken) {}

    @Transactional
    public UserView register(Register data) {
        String email = data.email().trim().toLowerCase(Locale.ROOT);
        String run = normalizeRun(data.run());
        if (users.findByEmail(email).isPresent() || users.existsByRun(run))
            throw ApiException.conflict("El RUN o correo ya está registrado.");
        checkPassword(data.password());
        var user = new AppUser();
        user.email = email; user.run = run; user.firstName = data.firstName().trim(); user.lastName = data.lastName().trim();
        if (user.firstName.isEmpty() || user.lastName.isEmpty()) throw ApiException.invalid("Nombre y apellidos son obligatorios.");
        user.passwordHash = passwords.encode(data.password());
        user.role = Role.CLIENTE; // El formulario público no admite un rol enviado por el cliente.
        return UserView.from(users.saveAndFlush(user));
    }

    @Transactional
    public Issued login(Login data) {
        var user = users.findByEmail(data.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
        boolean matches = passwords.matches(data.password(), user == null ? dummyHash : user.passwordHash);
        if (user == null || !user.active || !matches) throw unauthorized();
        return issue(user, UUID.randomUUID());
    }

    // Al detectar reutilización se revoca toda la familia, incluso si se devuelve 401.
    @Transactional(noRollbackFor = ApiException.class)
    public Issued refresh(String token) {
        if (token == null || token.length() > 200) throw unauthorized();
        var session = sessions.lockByHash(hash(token)).orElseThrow(AuthService::unauthorized);
        if (session.revoked || session.consumedAt != null || session.expiresAt.isBefore(Instant.now()) || !session.user.active) {
            sessions.revokeFamily(session.family);
            throw unauthorized();
        }
        session.consumedAt = Instant.now();
        return issue(session.user, session.family);
    }

    @Transactional
    public void logout(String token) {
        if (token != null && token.length() <= 200) sessions.lockByHash(hash(token)).ifPresent(session -> sessions.revokeFamily(session.family));
    }

    @Transactional(readOnly = true)
    public UserView identity(Long id) { return UserView.from(users.findById(id).filter(user -> user.active).orElseThrow(AuthService::unauthorized)); }

    private Issued issue(AppUser user, UUID family) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(config.issuer()).audience(List.of("kynex-web"))
            .subject(user.id.toString()).claim("sid", family.toString()).issuedAt(now).expiresAt(now.plus(config.accessDuration())).id(UUID.randomUUID().toString()).build();
        String access = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        var session = new RefreshSession(); session.family = family; session.user = user;
        session.tokenHash = hash(raw); session.expiresAt = now.plus(config.refreshDuration()); sessions.save(session);
        return new Issued(new Session(access, config.accessDuration().toSeconds(), UserView.from(user)), raw);
    }

    public static String normalizeRun(String value) {
        String run = value.replace(".", "").replace("-", "").trim().toUpperCase(Locale.ROOT);
        if (!run.matches("[0-9]{7,8}[0-9K]")) throw ApiException.invalid("El RUN no es válido.");
        int sum = 0, multiplier = 2;
        for (int i = run.length() - 2; i >= 0; i--) { sum += (run.charAt(i) - '0') * multiplier; multiplier = multiplier == 7 ? 2 : multiplier + 1; }
        int check = 11 - sum % 11;
        char expected = check == 11 ? '0' : check == 10 ? 'K' : (char)('0' + check);
        if (run.charAt(run.length() - 1) != expected) throw ApiException.invalid("El dígito verificador del RUN no es válido.");
        return run;
    }

    public static void checkPassword(String value) {
        if (value == null || value.length() < 10 || value.getBytes(StandardCharsets.UTF_8).length > 72)
            throw ApiException.invalid("La contraseña debe tener al menos 10 caracteres y no superar 72 bytes.");
    }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static ApiException unauthorized() { return new ApiException(HttpStatus.UNAUTHORIZED, "La sesión o las credenciales no son válidas."); }
}
