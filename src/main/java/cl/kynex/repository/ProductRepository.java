package cl.kynex.repository;

import cl.kynex.entity.Product;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @EntityGraph(attributePaths="category")
    List<Product> findByActiveTrueAndCategoryActiveTrueOrderById();
    boolean existsByCategoryIdAndActiveTrue(Long categoryId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id") Optional<Product> lockById(Long id);
}
