package cl.kynex.repository;
import cl.kynex.entity.PurchaseOrder;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface OrderRepository extends JpaRepository<PurchaseOrder,Long> {
    List<PurchaseOrder> findByOwnerIdOrderByIdDesc(Long ownerId);
    List<PurchaseOrder> findAllByOrderByIdDesc();
    Optional<PurchaseOrder> findByOwnerIdAndRequestId(Long ownerId, UUID requestId);
    @Query("select o.ownerId from PurchaseOrder o where o.id = :id") Optional<Long> ownerFor(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from PurchaseOrder o where o.id = :id") Optional<PurchaseOrder> lockById(Long id);
}
