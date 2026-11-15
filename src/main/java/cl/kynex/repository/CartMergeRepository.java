package cl.kynex.repository;
import cl.kynex.entity.CartMerge;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CartMergeRepository extends JpaRepository<CartMerge,Long> {
    Optional<CartMerge> findByUserIdAndRequestId(Long userId, UUID requestId);
}
