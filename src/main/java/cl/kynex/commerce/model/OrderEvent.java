package cl.kynex.commerce.model;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class OrderEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) public PurchaseOrder order;
    public Long actorId;
    @Enumerated(EnumType.STRING) public OrderStatus status;
    public String note;
    public Instant createdAt;
}
