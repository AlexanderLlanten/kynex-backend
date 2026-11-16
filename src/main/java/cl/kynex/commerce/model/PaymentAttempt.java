package cl.kynex.commerce.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
public class PaymentAttempt {
    @Id public UUID id = UUID.randomUUID();
    public Long orderId;
    public UUID requestId;
    @Enumerated(EnumType.STRING) public PaymentScenario scenario;
    @Enumerated(EnumType.STRING) public PaymentStatus status = PaymentStatus.PENDING;
    public BigDecimal amount;
    public Instant createdAt;
    public Instant expiresAt;
    public Instant resolvedAt;
    public boolean reversed;
}
