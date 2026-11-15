package cl.kynex.dto;
import cl.kynex.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class CommerceDtos {
    private CommerceDtos() {}
    public record CartQuantity(@NotNull @Min(0) Integer quantity, @NotNull @Min(0) Long version) {}
    public record GuestLine(@NotNull @Positive Long id, @NotNull @Positive Integer quantity) {}
    public record Merge(@NotNull UUID requestId, @NotNull @Size(max=100) List<@NotNull @Valid GuestLine> items) {}
    public record Delivery(@NotBlank @Size(max=150) String recipient, @NotBlank @Size(max=100) String region,
        @NotBlank @Size(max=100) String commune, @NotBlank @Size(max=300) String address) {}
    public record Checkout(@NotNull UUID requestId, @NotNull @Min(0) Long version, @NotNull @Valid Delivery delivery) {}
    public record PaymentInput(@NotNull UUID requestId, @NotNull PaymentScenario scenario) {}
    public record Confirmation() {}
    public record Transition(@NotNull OrderStatus status, @NotNull @Min(0) Long version, @NotBlank @Size(max=300) String reason) {}
    public record CartView(long version, List<Map<String,Object>> items, long totalItems,
        BigDecimal subtotal, BigDecimal shipping, BigDecimal total, BigDecimal missingForFreeShipping,
        boolean canCheckout, List<String> warnings) {}
    public record PaymentView(UUID id, PaymentScenario scenario, PaymentStatus status, BigDecimal amount,
        Instant createdAt, Instant expiresAt, Instant resolvedAt, boolean reversed) {
        public static PaymentView from(PaymentAttempt value) { return new PaymentView(value.id,value.scenario,value.status,value.amount,value.createdAt,value.expiresAt,value.resolvedAt,value.reversed); }
    }
    public record EventView(OrderStatus status, String note, Instant date) {}
    public record OrderView(Long id, OrderStatus status, long version, Instant date, Instant expiresAt,
        Map<String,String> customer, Delivery delivery, List<Map<String,Object>> products, long quantity,
        BigDecimal subtotal, BigDecimal shipping, BigDecimal total, List<PaymentView> payments, List<EventView> events) {}
}
