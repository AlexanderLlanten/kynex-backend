package cl.kynex.entity;
import jakarta.persistence.*;
import java.util.UUID;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Recibo de conciliación: un reintento con la misma clave no vuelve a sumar cantidades. */
@Entity
public class CartMerge {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public Long userId;
    public UUID requestId;
    public String fingerprint;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") public List<String> warnings = List.of();
}
