package cl.kynex.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class OrderLine {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) public PurchaseOrder order;
    public Long productId;
    public Long cartLineId;
    public String code;
    public String name;
    public String image;
    public BigDecimal price;
    public int quantity;
}
