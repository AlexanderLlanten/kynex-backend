package cl.kynex.repository;

import cl.kynex.entity.Product;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @EntityGraph(attributePaths="category")
    List<Product> findByActiveTrueAndCategoryActiveTrueOrderById();
    boolean existsByCategoryIdAndActiveTrue(Long categoryId);
}
