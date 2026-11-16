package cl.kynex.identity.service;

import cl.kynex.identity.api.AccountAccess;
import cl.kynex.identity.model.AppUser;
import cl.kynex.identity.model.Role;
import cl.kynex.identity.repository.AccountPolicyRepository;
import cl.kynex.identity.repository.UserRepository;
import cl.kynex.shared.exception.ApiException;
import java.util.Optional;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Identidad conserva sus repositorios y participa en la transacción iniciada por el módulo consumidor. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AccountAccessService implements AccountAccess {
    private final UserRepository users;
    private final AccountPolicyRepository policy;

    public AccountAccessService(UserRepository users, AccountPolicyRepository policy) {
        this.users = users;
        this.policy = policy;
    }

    @Override public Account lockActive(Long owner) {
        var user = Hibernate.unproxy(users.lockById(owner).orElseThrow(ApiException::missing), AppUser.class);
        if (!user.active) throw new ApiException(HttpStatus.UNAUTHORIZED, "La cuenta ya no está activa.");
        return snapshot(user);
    }

    @Override public void lockExisting(Long owner) {
        users.lockById(owner).orElseThrow(ApiException::missing);
    }

    @Override public Account requireAdministrator(Long actor) {
        policy.lockPolicy();
        var user = users.findById(actor).orElseThrow(ApiException::missing);
        if (!user.active || user.role != Role.ADMINISTRADOR)
            throw new ApiException(HttpStatus.FORBIDDEN, "Se requiere una cuenta de Administrador activa.");
        return snapshot(user);
    }

    @Override public Optional<Account> findActiveByEmail(String email) {
        return users.findByEmail(email).filter(user -> user.active).map(AccountAccessService::snapshot);
    }

    private static Account snapshot(AppUser user) {
        return new Account(user.id, user.firstName, user.lastName, user.email);
    }
}
