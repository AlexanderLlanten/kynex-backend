package cl.kynex.service;
import cl.kynex.dto.CommerceDtos.*;
import cl.kynex.entity.*;
import cl.kynex.exception.ApiException;
import cl.kynex.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pedidos con cotización histórica. Aprobar y anular inventario son transacciones completas. */
@Service
@Transactional
public class OrderService {
    private final CartService cartService;
    private final CartRepository carts;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final UserRepository users;
    private final LocationCatalog locations;
    private final PaymentGateway gateway;
    private final Clock clock;
    private final Duration lifetime;
    public OrderService(CartService cartService,CartRepository carts,OrderRepository orders,PaymentRepository payments,
            UserRepository users,LocationCatalog locations,PaymentGateway gateway,Clock clock,
            @Value("${kynex.commerce.order-duration:PT15M}") Duration lifetime) {
        this.cartService=cartService; this.carts=carts; this.orders=orders; this.payments=payments; this.users=users;
        this.locations=locations; this.gateway=gateway; this.clock=clock; this.lifetime=lifetime;
    }
    public OrderView checkout(Long owner,Checkout data) {
        var user=cartService.lockUser(owner);
        var address=new Delivery(data.delivery().recipient().trim(),data.delivery().region().trim(),data.delivery().commune().trim(),data.delivery().address().trim());
        locations.validate(address.region(),address.commune());
        if (address.recipient().isEmpty() || address.address().isEmpty()) throw ApiException.invalid("Completa el destinatario y la dirección.");
        String fingerprint=cartService.fingerprint(List.of(data.version(),address));
        var previous=orders.findByOwnerIdAndRequestId(owner,data.requestId());
        if (previous.isPresent()) {
            if (!previous.get().fingerprint.equals(fingerprint)) throw ApiException.conflict("La clave de compra ya se utilizó con otros datos.");
            return view(previous.get());
        }
        var cart=cartService.cart(owner); cartService.checkVersion(cart,data.version());
        if (cart.lines.isEmpty()) throw ApiException.invalid("El carrito está vacío.");
        var products=cartService.lockedProducts(cart.lines.stream().map(line -> cartService.product(line).id).toList());
        var order=new PurchaseOrder(); order.ownerId=owner; order.requestId=data.requestId(); order.fingerprint=fingerprint;
        order.customerName=user.firstName+" "+user.lastName; order.customerEmail=user.email;
        order.recipient=address.recipient(); order.region=address.region(); order.commune=address.commune(); order.address=address.address();
        order.createdAt=clock.instant(); order.expiresAt=order.createdAt.plus(lifetime); order.subtotal=BigDecimal.ZERO;
        for (var source:cart.lines) {
            var product=products.get(cartService.product(source).id);
            if (!cartService.available(product) || product.stock<source.quantity) throw ApiException.conflict("Revisa la disponibilidad de "+product.name+".");
            var line=new OrderLine(); line.order=order; line.productId=product.id; line.cartLineId=source.id;
            line.code=product.code; line.name=product.name; line.image=product.image; line.price=product.price; line.quantity=source.quantity;
            order.lines.add(line); order.subtotal=order.subtotal.add(line.price.multiply(BigDecimal.valueOf(line.quantity)));
        }
        order.shipping=CartService.shipping(order.subtotal); order.total=order.subtotal.add(order.shipping);
        event(order,owner,"Pedido pendiente; sin reserva ni descuento de inventario.");
        return view(orders.saveAndFlush(order));
    }
    private PurchaseOrder ownedLocked(Long owner,Long id) {
        if (!orders.ownerFor(id).orElseThrow(ApiException::missing).equals(owner)) throw ApiException.missing();
        cartService.lockUser(owner);
        return orders.lockById(id).orElseThrow(ApiException::missing);
    }
    public OrderView get(Long owner,Long id) { var order=ownedLocked(owner,id); expire(order); return view(orders.saveAndFlush(order)); }
    @Transactional(readOnly=true) public List<OrderView> mine(Long owner) { return orders.findByOwnerIdOrderByIdDesc(owner).stream().map(this::view).toList(); }
    @Transactional(readOnly=true) public List<OrderView> all() { return orders.findAllByOrderByIdDesc().stream().map(this::view).toList(); }
    @Transactional(readOnly=true) public OrderView managed(Long id) { return view(orders.findById(id).orElseThrow(ApiException::missing)); }
    public Map<String,Object> options() { return Map.of("enabled",gateway.enabled(),"scenarios",PaymentScenario.values()); }

    public PaymentView createPayment(Long owner,Long orderId,PaymentInput data) {
        var order=ownedLocked(owner,orderId);
        requireGateway();
        var old=payments.findByOrderIdAndRequestId(orderId,data.requestId());
        if (old.isPresent()) {
            if (old.get().scenario!=data.scenario()) throw ApiException.conflict("La clave de pago ya se utilizó con otro escenario.");
            return PaymentView.from(old.get());
        }
        if (order.status!=OrderStatus.PENDING || !clock.instant().isBefore(order.expiresAt)) throw ApiException.conflict("El pedido ya no admite un intento de pago.");
        for (var previous:payments.findByOrderIdOrderByCreatedAtAsc(order.id)) if (previous.status==PaymentStatus.PENDING) {
            if (clock.instant().isBefore(previous.expiresAt)) throw ApiException.conflict("El pedido ya tiene un pago pendiente de confirmación.");
            previous.status=PaymentStatus.EXPIRED; previous.resolvedAt=clock.instant();
        }
        var payment=new PaymentAttempt(); payment.orderId=orderId; payment.requestId=data.requestId(); payment.scenario=data.scenario();
        payment.amount=order.total; payment.createdAt=clock.instant(); payment.expiresAt=payment.createdAt.plus(Duration.ofMinutes(5));
        if (payment.expiresAt.isAfter(order.expiresAt)) payment.expiresAt=order.expiresAt;
        return PaymentView.from(payments.saveAndFlush(payment));
    }
    public OrderView confirm(Long owner,Long orderId,UUID paymentId) {
        var order=ownedLocked(owner,orderId); requireGateway();
        var payment=payments.findByIdAndOrderId(paymentId,orderId).orElseThrow(ApiException::missing);
        if (payment.status!=PaymentStatus.PENDING) return view(order);
        var now=clock.instant();
        if (!now.isBefore(order.expiresAt) || !now.isBefore(payment.expiresAt)) {
            payment.status=PaymentStatus.EXPIRED; payment.resolvedAt=now; expire(order); return view(orders.saveAndFlush(order));
        }
        if (order.status!=OrderStatus.PENDING) throw ApiException.conflict("El pedido ya no está pendiente.");
        var result=gateway.resolve(payment.scenario);
        if (result==PaymentStatus.APPROVED) {
            var stock=cartService.lockedProducts(order.lines.stream().map(line -> line.productId).toList());
            // Primero se validan todas las líneas. Ningún pago ni stock se confirma parcialmente.
            for (var line:order.lines) if (!cartService.available(stock.get(line.productId)) || stock.get(line.productId).stock<line.quantity)
                throw ApiException.conflict("Stock insuficiente de "+line.name+". El pago no fue aprobado.");
            for (var line:order.lines) stock.get(line.productId).stock-=line.quantity;
            order.status=OrderStatus.CONFIRMED; event(order,owner,"Pago aprobado en el emulador; inventario actualizado.");
            var cart=cartService.cart(owner);
            for (var bought:order.lines) cart.lines.removeIf(line -> {
                if (!line.id.equals(bought.cartLineId)) return false;
                line.quantity-=Math.min(line.quantity,bought.quantity); return line.quantity==0;
            });
            cart.revision++; carts.saveAndFlush(cart);
        }
        payment.status=result; payment.resolvedAt=now; payments.saveAndFlush(payment);
        return view(orders.saveAndFlush(order));
    }
    public OrderView transition(Long actor,Long id,Transition data) {
        Long owner=orders.ownerFor(id).orElseThrow(ApiException::missing);
        users.lockById(owner).orElseThrow(ApiException::missing);
        var order=orders.lockById(id).orElseThrow(ApiException::missing);
        if (order.status==data.status()) return view(order);
        if (!order.version.equals(data.version())) throw ApiException.conflict("El pedido cambió. Recarga antes de continuar.");
        var target=data.status();
        boolean cancel=target==OrderStatus.CANCELLED && Set.of(OrderStatus.PENDING,OrderStatus.CONFIRMED,OrderStatus.PREPARING).contains(order.status);
        boolean advance=(order.status==OrderStatus.CONFIRMED && target==OrderStatus.PREPARING)
            || (order.status==OrderStatus.PREPARING && target==OrderStatus.SHIPPED) || (order.status==OrderStatus.SHIPPED && target==OrderStatus.DELIVERED);
        if (!cancel && !advance) throw ApiException.conflict("La transición de estado no está permitida.");
        if (cancel) {
            if (order.status!=OrderStatus.PENDING && !order.stockRestored) {
                var stock=cartService.lockedProducts(order.lines.stream().map(line -> line.productId).toList());
                for (var line:order.lines) if ((long)stock.get(line.productId).stock+line.quantity>Integer.MAX_VALUE) throw ApiException.conflict("El inventario no admite esta restitución.");
                for (var line:order.lines) stock.get(line.productId).stock+=line.quantity;
                order.stockRestored=true;
            }
            for (var payment:payments.findByOrderIdOrderByCreatedAtAsc(id)) {
                if (payment.status==PaymentStatus.APPROVED) payment.reversed=true;
                else if (payment.status==PaymentStatus.PENDING) { payment.status=PaymentStatus.CANCELLED; payment.resolvedAt=clock.instant(); }
            }
        }
        order.status=target; event(order,actor,data.reason().trim()); return view(orders.saveAndFlush(order));
    }
    private void requireGateway() { if (!gateway.enabled()) throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"El emulador de pagos no está habilitado en este entorno."); }
    private void expire(PurchaseOrder order) {
        if (order.status==OrderStatus.PENDING && !clock.instant().isBefore(order.expiresAt)) {
            order.status=OrderStatus.EXPIRED; event(order,order.ownerId,"Venció el plazo del pedido pendiente.");
        }
    }
    private void event(PurchaseOrder order,Long actor,String note) {
        var event=new OrderEvent(); event.order=order; event.actorId=actor; event.status=order.status; event.note=note; event.createdAt=clock.instant(); order.events.add(event);
    }
    public OrderView view(PurchaseOrder order) {
        var items=new ArrayList<Map<String,Object>>(); long units=0;
        for (var line:order.lines) { items.add(Map.of("id",line.productId,"code",line.code,"name",line.name,"image",line.image,"price",line.price,"quantity",line.quantity)); units+=line.quantity; }
        OrderStatus effective=order.status==OrderStatus.PENDING && !clock.instant().isBefore(order.expiresAt)?OrderStatus.EXPIRED:order.status;
        return new OrderView(order.id,effective,order.version,order.createdAt,order.expiresAt,Map.of("name",order.customerName,"email",order.customerEmail),
            new Delivery(order.recipient,order.region,order.commune,order.address),items,units,order.subtotal,order.shipping,order.total,
            payments.findByOrderIdOrderByCreatedAtAsc(order.id).stream().map(PaymentView::from).toList(),
            order.events.stream().map(value -> new EventView(value.status,value.note,value.createdAt)).toList());
    }
}
