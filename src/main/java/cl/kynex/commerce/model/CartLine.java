package cl.kynex.commerce.model;

import cl.kynex.catalog.model.Product;
import jakarta.persistence.*;

@Entity
public class CartLine {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="cart_user_id") public ShoppingCart cart;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) public Product product;
    public int quantity;
}
