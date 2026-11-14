package cl.kynex.dto;

import cl.kynex.entity.AppUser;
import jakarta.validation.constraints.*;

public final class AuthDtos {
    private AuthDtos() {}
    public record Register(@NotBlank @Size(max=12) String run,
        @NotBlank @Size(max=50) String firstName, @NotBlank @Size(max=100) String lastName,
        @NotBlank @Email @Size(max=100) String email, @NotBlank @Size(min=10, max=72) String password) {}
    public record Login(@NotBlank @Email @Size(max=100) String email, @NotBlank @Size(max=72) String password) {}
    public record UserView(Long id, String run, String firstName, String lastName, String email, String role) {
        public static UserView from(AppUser user) {
            return new UserView(user.id, user.run, user.firstName, user.lastName, user.email, switch(user.role) {
                case CLIENTE -> "Cliente"; case VENDEDOR -> "Vendedor"; case ADMINISTRADOR -> "Administrador";
            });
        }
    }
    public record Session(String accessToken, long expiresIn, UserView user) {}
}
