package cl.kynex.config;

import cl.kynex.entity.*;
import cl.kynex.repository.UserRepository;
import cl.kynex.service.AuthService;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Alta inicial explícita mediante variables de entorno, sin contraseña predeterminada. */
@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;
    public BootstrapAdmin(UserRepository users, PasswordEncoder passwords,
        @Value("${kynex.bootstrap.admin-email}") String email, @Value("${kynex.bootstrap.admin-password}") String password) {
        this.users = users; this.passwords = passwords; this.email = email.trim().toLowerCase(Locale.ROOT); this.password = password;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        if (email.isEmpty() && password.isEmpty()) return;
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new IllegalArgumentException("El correo inicial de administración no es válido.");
        AuthService.checkPassword(password);
        if (users.findByEmail(email).isPresent()) return;
        var admin = new AppUser(); admin.firstName = "Administración"; admin.lastName = "KYNEX";
        admin.email = email; admin.passwordHash = passwords.encode(password); admin.role = Role.ADMINISTRADOR;
        users.save(admin);
    }
}
