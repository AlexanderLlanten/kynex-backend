package cl.kynex.service;
import cl.kynex.dto.UserDtos.*;
import cl.kynex.entity.*;
import cl.kynex.exception.ApiException;
import cl.kynex.repository.*;
import java.util.*;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Perfil propio y administración con baja lógica, versiones e historial conservado. */
@Service
@Transactional
public class UserService {
    private final UserRepository users;
    private final AccountPolicyRepository policy;
    private final RefreshSessionRepository sessions;
    private final AuthService auth;
    private final LocationCatalog locations;
    public UserService(UserRepository users,AccountPolicyRepository policy,RefreshSessionRepository sessions,AuthService auth,LocationCatalog locations) {
        this.users=users;this.policy=policy;this.sessions=sessions;this.auth=auth;this.locations=locations;
    }
    private AppUser find(Long id) { return users.findById(id).orElseThrow(ApiException::missing); }
    private AppUser locked(Long id) { return Hibernate.unproxy(users.lockById(id).orElseThrow(ApiException::missing),AppUser.class); }
    private void admin(Long actor) {
        var user=find(actor);
        if (!user.active || user.role!=Role.ADMINISTRADOR) throw new ApiException(HttpStatus.FORBIDDEN,"La operación requiere una cuenta de Administrador activa.");
    }
    private void version(AppUser user,Long expected) {
        if (!user.version.equals(expected)) throw ApiException.conflict("La cuenta cambió. Recarga sus datos antes de guardar.");
    }
    @Transactional(readOnly=true) public List<AccountView> all(Long actor) {
        admin(actor); return users.findAllByOrderById().stream().map(AccountView::from).toList();
    }
    @Transactional(readOnly=true) public AccountView get(Long actor,Long id) { admin(actor);return AccountView.from(find(id)); }
    @Transactional(readOnly=true) public AccountView profile(Long owner) {
        var user=find(owner);if (!user.active) throw new ApiException(HttpStatus.UNAUTHORIZED,"La cuenta no está activa.");
        return AccountView.from(user);
    }
    public AccountView create(Long actor,Create data) {
        policy.lockPolicy();admin(actor);
        var registered=auth.register(data.profile());var user=find(registered.id());user.role=data.role();
        return AccountView.from(users.saveAndFlush(user));
    }
    public AccountView update(Long actor,Long id,Update data) {
        policy.lockPolicy();admin(actor);var user=locked(id);version(user,data.version());
        if (actor.equals(id) && data.role()!=user.role) throw ApiException.conflict("No puedes cambiar tu propio rol desde Administración.");
        protectLastAdmin(user,data.role(),user.active);apply(user,data.profile());user.role=data.role();
        return AccountView.from(users.saveAndFlush(user));
    }
    public void deactivate(Long actor,Long id,Long expected) {
        policy.lockPolicy();admin(actor);var user=locked(id);version(user,expected);
        if (actor.equals(id)) throw ApiException.conflict("No puedes desactivar tu propia cuenta.");
        protectLastAdmin(user,user.role,false);user.active=false;
        users.saveAndFlush(user);sessions.revokeUser(id);
    }
    public AccountView activate(Long actor,Long id,Long expected) {
        policy.lockPolicy();admin(actor);var user=locked(id);version(user,expected);user.active=true;
        return AccountView.from(users.saveAndFlush(user));
    }
    public AccountView updateProfile(Long owner,ProfileInput data) {
        var user=locked(owner);if (!user.active) throw new ApiException(HttpStatus.UNAUTHORIZED,"La cuenta no está activa.");
        version(user,data.version());apply(user,data.profile());
        return AccountView.from(users.saveAndFlush(user));
    }
    private void protectLastAdmin(AppUser user,Role role,boolean active) {
        if (user.active && user.role==Role.ADMINISTRADOR && (!active || role!=Role.ADMINISTRADOR)
                && users.countByRoleAndActiveTrue(Role.ADMINISTRADOR)<=1)
            throw ApiException.conflict("Debe conservarse al menos una cuenta de Administrador activa.");
    }
    private void apply(AppUser user,ProfileData data) {
        var run=AuthService.normalizeRun(data.run());var email=data.email().trim().toLowerCase(Locale.ROOT);
        if (users.findByEmail(email).filter(other -> !other.id.equals(user.id)).isPresent() || users.existsByRunAndIdNot(run,user.id))
            throw ApiException.conflict("El RUN o correo ya está registrado.");
        locations.validate(data.region().trim(),data.commune().trim());
        user.run=run;user.email=email;user.firstName=data.firstName().trim();user.lastName=data.lastName().trim();
        user.birthDate=data.birthDate();user.region=data.region().trim();user.commune=data.commune().trim();user.address=data.address().trim();
        if (user.firstName.isEmpty() || user.lastName.isEmpty() || user.address.isEmpty()) throw ApiException.invalid("Completa nombre, apellidos y dirección.");
    }
}
