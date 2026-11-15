package cl.kynex;
import cl.kynex.entity.*;
import cl.kynex.dto.CommerceDtos.*;
import cl.kynex.exception.ApiException;
import cl.kynex.repository.*;
import cl.kynex.service.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="kynex.commerce.emulator-enabled=false")
@ActiveProfiles("test") @ContextConfiguration(initializers=TestDatabaseGuard.class)
@Transactional
class PaymentConfigurationTest {
    @Autowired CartService cart;
    @Autowired OrderService shop;
    @Autowired UserRepository users;
    @Autowired PaymentRepository payments;
    @Test void disabledEmulatorCannotCreateAChargeEvenWhenClientRequestsApproval() {
        var user=new AppUser(); user.firstName="Prueba"; user.lastName="Configuración"; user.email=UUID.randomUUID()+"@example.com"; user.passwordHash="not-used";
        user=users.saveAndFlush(user); Long owner=user.id;
        var current=cart.get(owner); current=cart.quantity(owner,2L,new CartQuantity(1,current.version()));
        var order=shop.checkout(owner,new Checkout(UUID.randomUUID(),current.version(),CommerceIntegrationTest.DELIVERY));
        assertThat(shop.options().get("enabled")).isEqualTo(false);
        assertThatThrownBy(() -> shop.createPayment(owner,order.id(),new PaymentInput(UUID.randomUUID(),PaymentScenario.APPROVE)))
            .isInstanceOfSatisfying(ApiException.class,error -> assertThat(error.status.value()).isEqualTo(503));
        assertThat(payments.findByOrderIdOrderByCreatedAtAsc(order.id())).isEmpty();
    }
}
