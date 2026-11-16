package cl.kynex.catalog.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Producto comercial; los metadatos nutricionales se conservan separados del precio y stock. */
@Entity
public class Product {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) public Category category;
    public String code;
    public String name;
    public String description;
    public BigDecimal price;
    public int stock;
    public Integer criticalStock;
    public String image = "";
    public boolean featured;
    public boolean active = true;
    @JdbcTypeCode(SqlTypes.JSON) public Map<String, Object> details = new LinkedHashMap<>();
    @Version public Long version;
}
