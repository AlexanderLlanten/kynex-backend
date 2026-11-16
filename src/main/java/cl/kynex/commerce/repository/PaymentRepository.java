package cl.kynex.commerce.repository;
import cl.kynex.commerce.model.PaymentAttempt;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentRepository extends JpaRepository<PaymentAttempt,UUID> {
    List<PaymentAttempt> findByOrderIdOrderByCreatedAtAsc(Long orderId);
    Optional<PaymentAttempt> findByOrderIdAndRequestId(Long orderId, UUID requestId);
    Optional<PaymentAttempt> findByIdAndOrderId(UUID id, Long orderId);
}
