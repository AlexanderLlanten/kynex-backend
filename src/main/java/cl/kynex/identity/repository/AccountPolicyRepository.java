package cl.kynex.identity.repository;
import cl.kynex.identity.model.AccountPolicy;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

public interface AccountPolicyRepository extends JpaRepository<AccountPolicy,Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AccountPolicy p where p.id = 1") AccountPolicy lockPolicy();
}
