package cl.kynex.identity.controller;
import cl.kynex.identity.dto.UserDtos.*;
import cl.kynex.identity.service.UserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class UserController {
    private final UserService users;
    public UserController(UserService users) { this.users=users; }
    private Long id(Principal principal) { return Long.valueOf(principal.getName()); }
    @GetMapping("/profile") public AccountView profile(Principal actor) { return users.profile(id(actor)); }
    @PutMapping("/profile") public AccountView profile(Principal actor,@Valid @RequestBody ProfileInput data) { return users.updateProfile(id(actor),data); }
    @GetMapping("/admin/users") @PreAuthorize("hasRole('ADMINISTRADOR')") public List<AccountView> all(Principal actor) { return users.all(id(actor)); }
    @GetMapping("/admin/users/{userId}") @PreAuthorize("hasRole('ADMINISTRADOR')") public AccountView get(Principal actor,@PathVariable Long userId) { return users.get(id(actor),userId); }
    @PostMapping("/admin/users") @PreAuthorize("hasRole('ADMINISTRADOR')") @ResponseStatus(HttpStatus.CREATED)
    public AccountView create(Principal actor,@Valid @RequestBody Create data) { return users.create(id(actor),data); }
    @PutMapping("/admin/users/{userId}") @PreAuthorize("hasRole('ADMINISTRADOR')")
    public AccountView update(Principal actor,@PathVariable Long userId,@Valid @RequestBody Update data) { return users.update(id(actor),userId,data); }
    @DeleteMapping("/admin/users/{userId}") @PreAuthorize("hasRole('ADMINISTRADOR')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(Principal actor,@PathVariable Long userId,@RequestParam Long version) { users.deactivate(id(actor),userId,version); }
    @PutMapping("/admin/users/{userId}/activation") @PreAuthorize("hasRole('ADMINISTRADOR')")
    public AccountView activate(Principal actor,@PathVariable Long userId,@Valid @RequestBody Version data) { return users.activate(id(actor),userId,data.version()); }
}
