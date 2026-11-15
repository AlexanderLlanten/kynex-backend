package cl.kynex.dto;
import cl.kynex.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** Entradas explícitas: el perfil personal no admite id, rol, estado ni contraseña. */
public final class UserDtos {
    private UserDtos() {}
    public record ProfileData(@NotBlank @Size(max=12) String run,
        @NotBlank @Size(max=50) String firstName, @NotBlank @Size(max=100) String lastName,
        @NotBlank @Email @Size(max=100) String email, @Past LocalDate birthDate,
        @NotBlank @Size(max=100) String region, @NotBlank @Size(max=100) String commune,
        @NotBlank @Size(max=300) String address) {}
    public record ProfileInput(@NotNull @Min(0) Long version, @NotNull @Valid ProfileData profile) {}
    public record Create(@NotNull @Valid AuthDtos.Register profile, @NotNull Role role) {}
    public record Update(@NotNull @Min(0) Long version, @NotNull @Valid ProfileData profile, @NotNull Role role) {}
    public record Version(@NotNull @Min(0) Long version) {}
    public record AccountView(Long id, String run, String firstName, String lastName, String email,
        String role, LocalDate birthDate, String region, String commune, String address, boolean active, long version) {
        public static AccountView from(AppUser user) {
            return new AccountView(user.id,user.run,user.firstName,user.lastName,user.email,
                AuthDtos.UserView.from(user).role(),user.birthDate,user.region,user.commune,user.address,user.active,user.version);
        }
    }
}
