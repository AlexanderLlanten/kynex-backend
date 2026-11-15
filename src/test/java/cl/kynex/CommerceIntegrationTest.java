package cl.kynex;
import cl.kynex.dto.CommerceDtos.*;
import cl.kynex.entity.*;
import cl.kynex.exception.ApiException;
import cl.kynex.repository.*;
import cl.kynex.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="kynex.commerce.emulator-enabled=true")
@AutoConfigureMockMvc @ActiveProfiles("test")
@ContextConfiguration(initializers=TestDatabaseGuard.class)
@Transactional
class CommerceIntegrationTest {
    @Autowired CartService cart;
    @Autowired OrderService shop;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired TestClock time;
    @Autowired JdbcTemplate jdbc;
    Long buyer;
    static final Delivery DELIVERY=new Delivery("Camila Soto","Región Metropolitana de Santiago","Santiago","Calle de prueba 123");
    static final String HASH=new BCryptPasswordEncoder(4).encode("ClavePrueba2026!");
    @TestConfiguration static class TimeConfig { @Bean @Primary TestClock testClock() { return new TestClock(); } }
    static class TestClock extends Clock {
        final AtomicReference<Instant> instant=new AtomicReference<>(Instant.parse("2026-11-14T12:00:00Z"));
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return instant.get(); }
    }
    @BeforeEach void setup() { time.instant.set(Instant.parse("2026-11-14T12:00:00Z")); buyer=user(Role.CLIENTE).id; }
    AppUser user(Role role) {
        var user=new AppUser(); user.firstName="Camila"; user.lastName="Soto"; user.email=UUID.randomUUID()+"@example.com"; user.passwordHash=HASH; user.role=role;
        return users.saveAndFlush(user);
    }
    CartView add(Long owner,int quantity) { var current=cart.get(owner); return cart.quantity(owner,2L,new CartQuantity(quantity,current.version())); }
    OrderView order(Long owner,int quantity) { var current=add(owner,quantity); return shop.checkout(owner,new Checkout(UUID.randomUUID(),current.version(),DELIVERY)); }
    PaymentView payment(Long owner,Long id,PaymentScenario scenario) { return shop.createPayment(owner,id,new PaymentInput(UUID.randomUUID(),scenario)); }
    @Test void accountCartIsIsolatedAndDoesNotReserveStock() {
        var mine=add(buyer,2); assertThat(mine.totalItems()).isEqualTo(2); assertThat(mine.total()).isEqualByComparingTo("47970");
        assertThat(cart.get(user(Role.CLIENTE).id).items()).isEmpty(); assertThat(products.findById(2L).orElseThrow().stock).isEqualTo(25);
    }
    @Test void guestMergeIsIdempotentAndRejectsChangedPayload() {
        var key=UUID.randomUUID(); var input=new Merge(key,List.of(new GuestLine(2L,2)));
        cart.merge(buyer,input); cart.merge(buyer,input); assertThat(cart.get(buyer).totalItems()).isEqualTo(2);
        assertThatThrownBy(() -> cart.merge(buyer,new Merge(key,List.of(new GuestLine(2L,3))))).isInstanceOf(ApiException.class);
    }
    @Test void mergeCapsStockAndReportsUnavailableProducts() {
        var input=new Merge(UUID.randomUUID(),List.of(new GuestLine(2L,100),new GuestLine(99999L,1)));
        var result=cart.merge(buyer,input);
        assertThat(result.totalItems()).isEqualTo(25); assertThat(result.warnings()).hasSize(2);
        assertThat(cart.merge(buyer,input).warnings()).isEqualTo(result.warnings());
    }
    @Test void staleCartVersionDoesNotOverwriteAnotherChange() {
        add(buyer,2); assertThatThrownBy(() -> cart.quantity(buyer,2L,new CartQuantity(3,0L))).isInstanceOf(ApiException.class);
        assertThat(cart.get(buyer).totalItems()).isEqualTo(2);
    }
    @Test void invalidAvailabilityPreventsCheckoutWithoutDeletingCart() {
        add(buyer,3); var product=products.findById(2L).orElseThrow(); product.stock=1; products.saveAndFlush(product);
        var current=cart.get(buyer); assertThat(current.canCheckout()).isFalse();
        assertThatThrownBy(() -> shop.checkout(buyer,new Checkout(UUID.randomUUID(),current.version(),DELIVERY))).isInstanceOf(ApiException.class);
        assertThat(cart.get(buyer).totalItems()).isEqualTo(3);
    }
    @Test void pendingOrderUsesServerPricesAndFreeShippingWithoutChangingStock() {
        var order=order(buyer,3); assertThat(order.subtotal()).isEqualByComparingTo("65970"); assertThat(order.shipping()).isZero();
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING); assertThat(products.findById(2L).orElseThrow().stock).isEqualTo(25);
        assertThat(order.delivery()).isEqualTo(DELIVERY);
    }
    @Test void checkoutIdempotencyReturnsSameOrderAfterCartChanges() {
        var current=add(buyer,1); var data=new Checkout(UUID.randomUUID(),current.version(),DELIVERY);
        var first=shop.checkout(buyer,data); add(buyer,2); var again=shop.checkout(buyer,data);
        assertThat(again.id()).isEqualTo(first.id()); assertThat(orders.findByOwnerIdOrderByIdDesc(buyer)).hasSize(1);
    }
    @Test void sameCheckoutKeyCannotChangeDelivery() {
        var current=add(buyer,1); var key=UUID.randomUUID(); shop.checkout(buyer,new Checkout(key,current.version(),DELIVERY));
        assertThatThrownBy(() -> shop.checkout(buyer,new Checkout(key,current.version(),new Delivery("Otra persona",DELIVERY.region(),DELIVERY.commune(),"Otra calle")))).isInstanceOf(ApiException.class);
    }
    @Test void approvedPaymentDeductsStockExactlyOnce() {
        var order=order(buyer,2); var payment=payment(buyer,order.id(),PaymentScenario.APPROVE);
        shop.confirm(buyer,order.id(),payment.id()); var repeat=shop.confirm(buyer,order.id(),payment.id());
        assertThat(repeat.status()).isEqualTo(OrderStatus.CONFIRMED); assertThat(products.findById(2L).orElseThrow().stock).isEqualTo(23);
        assertThat(cart.get(buyer).items()).isEmpty(); assertThat(repeat.payments().getFirst().amount()).isEqualByComparingTo(order.total());
    }
    @Test void successRemovesOnlyPurchasedUnitsAndPreservesNewAdditions() {
        var order=order(buyer,1); add(buyer,4); var payment=payment(buyer,order.id(),PaymentScenario.APPROVE);
        shop.confirm(buyer,order.id(),payment.id()); assertThat(cart.get(buyer).totalItems()).isEqualTo(3);
    }
    @Test void successDoesNotRemoveAnItemDeletedAndAddedAgain() {
        var order=order(buyer,1); var current=cart.get(buyer); cart.clear(buyer,current.version()); add(buyer,2);
        shop.confirm(buyer,order.id(),payment(buyer,order.id(),PaymentScenario.APPROVE).id());
        assertThat(cart.get(buyer).totalItems()).isEqualTo(2);
    }
    @Test void declinedCancelledExpiredAndErrorDoNotConsumeInventory() {
        for (var scenario:List.of(PaymentScenario.REJECT,PaymentScenario.CANCEL,PaymentScenario.EXPIRE,PaymentScenario.TECHNICAL_ERROR)) {
            var order=order(buyer,1); var payment=payment(buyer,order.id(),scenario);
            var result=shop.confirm(buyer,order.id(),payment.id());
            assertThat(result.status()).isEqualTo(OrderStatus.PENDING); assertThat(result.payments().getFirst().status()).isNotEqualTo(PaymentStatus.APPROVED);
            assertThat(products.findById(2L).orElseThrow().stock).isEqualTo(25); assertThat(cart.get(buyer).totalItems()).isEqualTo(1);
        }
    }
    @Test void rejectedPaymentCanBeRetriedWithAnotherAttempt() {
        var order=order(buyer,1); shop.confirm(buyer,order.id(),payment(buyer,order.id(),PaymentScenario.REJECT).id());
        var result=shop.confirm(buyer,order.id(),payment(buyer,order.id(),PaymentScenario.APPROVE).id());
        assertThat(result.payments()).hasSize(2); assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
    }
    @Test void paymentCreationDeduplicatesItsKeyAndRejectsTwoPendingAttempts() {
        var order=order(buyer,1); var input=new PaymentInput(UUID.randomUUID(),PaymentScenario.APPROVE);
        var first=shop.createPayment(buyer,order.id(),input); assertThat(shop.createPayment(buyer,order.id(),input).id()).isEqualTo(first.id());
        assertThatThrownBy(() -> payment(buyer,order.id(),PaymentScenario.REJECT)).isInstanceOf(ApiException.class);
    }
    @Test void lackOfStockRollsBackApprovalAndOtherInventoryLines() {
        var order=order(buyer,2); var attempt=payment(buyer,order.id(),PaymentScenario.APPROVE);
        var product=products.findById(2L).orElseThrow(); product.stock=1; products.saveAndFlush(product);
        assertThatThrownBy(() -> shop.confirm(buyer,order.id(),attempt.id())).isInstanceOf(ApiException.class);
        assertThat(payments.findById(attempt.id()).orElseThrow().status).isEqualTo(PaymentStatus.PENDING);
        assertThat(orders.findById(order.id()).orElseThrow().status).isEqualTo(OrderStatus.PENDING);
    }
    @Test void expiredOrderCannotBePaidAndIsRecordedAsExpired() {
        var order=order(buyer,1); var attempt=payment(buyer,order.id(),PaymentScenario.APPROVE);
        time.instant.set(time.instant().plusSeconds(1000)); var result=shop.confirm(buyer,order.id(),attempt.id());
        assertThat(result.status()).isEqualTo(OrderStatus.EXPIRED); assertThat(result.payments().getFirst().status()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(products.findById(2L).orElseThrow().stock).isEqualTo(25);
    }
    @Test void cancelledConfirmedOrderRestoresStockOnlyOnceAndRecordsSimulatedReversal() {
        var order=order(buyer,2); var confirmed=shop.confirm(buyer,order.id(),payment(buyer,order.id(),PaymentScenario.APPROVE).id());
        var input=new Transition(OrderStatus.CANCELLED,confirmed.version(),"Anulación de prueba");
        var admin=user(Role.ADMINISTRADOR); shop.transition(admin.id,order.id(),input); var repeated=shop.transition(admin.id,order.id(),input);
        assertThat(products.findById(2L).orElseThrow().stock).isEqualTo(25); assertThat(repeated.payments().getFirst().reversed()).isTrue();
        assertThat(repeated.events()).hasSize(3);
    }
    @Test void cancellationOfPendingOrderDoesNotAddStock() {
        var order=order(buyer,1); shop.transition(user(Role.ADMINISTRADOR).id,order.id(),new Transition(OrderStatus.CANCELLED,order.version(),"Cancelar pendiente"));
        assertThat(products.findById(2L).orElseThrow().stock).isEqualTo(25);
    }
    @Test void invalidTransitionsAndStaleVersionsAreRejected() {
        var order=order(buyer,1); var admin=user(Role.ADMINISTRADOR);
        assertThatThrownBy(() -> shop.transition(admin.id,order.id(),new Transition(OrderStatus.DELIVERED,order.version(),"Sin pago"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> shop.transition(admin.id,order.id(),new Transition(OrderStatus.CANCELLED,99L,"Versión antigua"))).isInstanceOf(ApiException.class);
    }
    @Test void anotherOwnerCannotReadOrPayAnOrder() {
        var order=order(buyer,1); var another=user(Role.CLIENTE).id;
        assertThatThrownBy(() -> shop.get(another,order.id())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> payment(another,order.id(),PaymentScenario.APPROVE)).isInstanceOf(ApiException.class);
    }
    @Test void historicalReceiptDoesNotFollowCatalogEdits() {
        var order=order(buyer,1); var product=products.findById(2L).orElseThrow(); product.price=new BigDecimal("1"); product.name="Editado"; products.saveAndFlush(product);
        var receipt=shop.get(buyer,order.id()); assertThat(receipt.products().getFirst().get("name")).isEqualTo("Creatina Monohidratada");
        assertThat(receipt.subtotal()).isEqualByComparingTo("21990");
    }
    String token(AppUser user) throws Exception {
        var csrf=http.perform(get("/api/v1/auth/csrf")).andReturn().getResponse();
        var response=http.perform(post("/api/v1/auth/login").cookie(csrf.getCookie("XSRF-TOKEN"))
            .header("X-XSRF-TOKEN",json.readTree(csrf.getContentAsString()).get("token").asText())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email",user.email,"password","ClavePrueba2026!"))))
            .andExpect(status().isOk()).andReturn().getResponse();
        return "Bearer "+json.readTree(response.getContentAsString()).get("accessToken").asText();
    }
    @Test void apiRejectsFractionalQuantitiesAndUntrustedAmounts() throws Exception {
        var user=users.findById(buyer).orElseThrow(); var token=token(user);
        http.perform(put("/api/v1/cart/items/2").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1.5,\"version\":0}")).andExpect(status().isBadRequest());
        http.perform(post("/api/v1/orders").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"total\":1}")).andExpect(status().isBadRequest());
        http.perform(post("/api/v1/cart/merge").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"requestId\":\""+UUID.randomUUID()+"\",\"items\":[null]}")).andExpect(status().isBadRequest());
    }
    @Test void sellerReadsOrdersButCannotChangeTheirState() throws Exception {
        var order=order(buyer,1); var token=token(user(Role.VENDEDOR));
        http.perform(get("/api/v1/admin/orders").header("Authorization",token)).andExpect(status().isOk());
        http.perform(put("/api/v1/admin/orders/"+order.id()+"/status").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new Transition(OrderStatus.CANCELLED,order.version(),"Intento vendedor")))).andExpect(status().isForbidden());
    }
    @Test void browserCannotImposeApprovedStatus() throws Exception {
        var order=order(buyer,1); var attempt=payment(buyer,order.id(),PaymentScenario.REJECT); var token=token(users.findById(buyer).orElseThrow());
        http.perform(post("/api/v1/orders/"+order.id()+"/payments/"+attempt.id()+"/confirm").header("Authorization",token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}")).andExpect(status().isBadRequest());
        assertThat(shop.confirm(buyer,order.id(),attempt.id()).payments().getFirst().status()).isEqualTo(PaymentStatus.REJECTED);
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void competingBuyersAndDuplicateConfirmationsNeverOversell() throws Exception {
        var initial=products.findById(2L).orElseThrow(); int stock=initial.stock; long version=initial.version;
        var owners=new ArrayList<Long>(); owners.add(buyer);
        try {
            jdbc.update("UPDATE product SET stock=1 WHERE id=2");
            var another=user(Role.CLIENTE).id; owners.add(another);
            var first=order(buyer,1); var second=order(another,1);
            var firstPayment=payment(buyer,first.id(),PaymentScenario.APPROVE); var secondPayment=payment(another,second.id(),PaymentScenario.APPROVE);
            var results=parallel(() -> approve(buyer,first.id(),firstPayment.id()), () -> approve(another,second.id(),secondPayment.id()));
            assertThat(results).containsExactlyInAnyOrder(true,false);
            assertThat(products.findById(2L).orElseThrow().stock).isZero();
            jdbc.update("UPDATE product SET stock=1 WHERE id=2");
            var third=user(Role.CLIENTE).id; owners.add(third); var current=add(third,1);
            var input=new Checkout(UUID.randomUUID(),current.version(),DELIVERY);
            parallel(() -> { shop.checkout(third,input); return true; }, () -> { shop.checkout(third,input); return true; });
            assertThat(orders.findByOwnerIdOrderByIdDesc(third)).hasSize(1);
            var thirdOrder=orders.findByOwnerIdOrderByIdDesc(third).getFirst(); var attempt=payment(third,thirdOrder.id,PaymentScenario.APPROVE);
            assertThat(parallel(() -> approve(third,thirdOrder.id,attempt.id()), () -> approve(third,thirdOrder.id,attempt.id()))).containsExactly(true,true);
            assertThat(products.findById(2L).orElseThrow().stock).isZero();
        } finally {
            for (var owner:owners) {
                for (var table:List.of("order_event","payment_attempt","order_line")) jdbc.update("DELETE FROM "+table+" WHERE order_id IN (SELECT id FROM purchase_order WHERE owner_id=?)",owner);
                jdbc.update("DELETE FROM purchase_order WHERE owner_id=?",owner);
                jdbc.update("DELETE FROM cart_line WHERE cart_user_id=?",owner);
                jdbc.update("DELETE FROM cart_merge WHERE user_id=?",owner);
                jdbc.update("DELETE FROM shopping_cart WHERE user_id=?",owner);
                jdbc.update("DELETE FROM app_user WHERE id=?",owner);
            }
            jdbc.update("UPDATE product SET stock=?, version=? WHERE id=2",stock,version);
        }
    }
    boolean approve(Long owner,Long order,UUID payment) { try { shop.confirm(owner,order,payment); return true; } catch(ApiException ex) { return false; } }
    List<Boolean> parallel(Callable<Boolean> first,Callable<Boolean> second) throws Exception {
        try (var pool=Executors.newFixedThreadPool(2)) {
            var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
            var a=pool.submit(() -> { ready.countDown(); start.await(); return first.call(); });
            var b=pool.submit(() -> { ready.countDown(); start.await(); return second.call(); });
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
    }
}
