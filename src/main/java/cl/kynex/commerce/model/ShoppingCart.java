package cl.kynex.commerce.model;
import jakarta.persistence.*;
import java.util.*;

/** La identidad del carrito es la del propietario, nunca un identificador elegido por el navegador. */
@Entity
public class ShoppingCart {
    @Id public Long userId;
    public long revision;
    @OneToMany(mappedBy="cart", cascade=CascadeType.ALL, orphanRemoval=true)
    @OrderBy("id ASC") public List<CartLine> lines = new ArrayList<>();
}
