package cl.kynex.repository;
import cl.kynex.entity.ShoppingCart;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CartRepository extends JpaRepository<ShoppingCart,Long> {}
