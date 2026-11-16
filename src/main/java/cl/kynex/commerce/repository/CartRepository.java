package cl.kynex.commerce.repository;
import cl.kynex.commerce.model.ShoppingCart;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CartRepository extends JpaRepository<ShoppingCart,Long> {}
