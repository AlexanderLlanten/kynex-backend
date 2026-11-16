package cl.kynex.commerce.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Boleta/pedido con importes y dirección históricos; los precios no dependen de ediciones futuras. */
@Entity
public class PurchaseOrder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public Long ownerId;
    public UUID requestId;
    public String fingerprint;
    @Enumerated(EnumType.STRING) public OrderStatus status = OrderStatus.PENDING;
    public String customerName;
    public String customerEmail;
    public String recipient;
    public String region;
    public String commune;
    public String address;
    public BigDecimal subtotal;
    public BigDecimal shipping;
    public BigDecimal total;
    public Instant createdAt;
    public Instant expiresAt;
    public boolean stockRestored;
    @Version public Long version;
    @OneToMany(mappedBy="order", cascade=CascadeType.ALL) @OrderBy("id ASC") public List<OrderLine> lines = new ArrayList<>();
    @OneToMany(mappedBy="order", cascade=CascadeType.ALL) @OrderBy("id ASC") public List<OrderEvent> events = new ArrayList<>();
}
