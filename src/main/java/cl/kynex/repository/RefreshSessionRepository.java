package cl.kynex.repository;

import cl.kynex.entity.RefreshSession;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
import org.springframework.data.jpa.repository.*;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    // El bloqueo evita que dos renovaciones consuman a la vez el mismo token.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s join fetch s.user where s.tokenHash = :hash")
    Optional<RefreshSession> lockByHash(String hash);

    boolean existsByFamilyAndRevokedFalseAndExpiresAtAfter(UUID family, Instant instant);

    @Modifying(clearAutomatically=true, flushAutomatically=true)
    @Query("update RefreshSession s set s.revoked = true where s.family = :family")
    void revokeFamily(UUID family);
}
