package cl.kynex.commerce.controller;

import cl.kynex.commerce.service.CartService;
import cl.kynex.commerce.service.OrderService;
import cl.kynex.commerce.dto.CommerceDtos.*;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CommerceController {
    private final CartService carts;
    private final OrderService orders;
    public CommerceController(CartService carts,OrderService orders) { this.carts=carts; this.orders=orders; }
    private Long owner(Principal principal) { return Long.valueOf(principal.getName()); }
    @GetMapping("/cart") public CartView cart(Principal user) { return carts.get(owner(user)); }
    @PutMapping("/cart/items/{id}") public CartView quantity(Principal user,@PathVariable Long id,@Valid @RequestBody CartQuantity data) { return carts.quantity(owner(user),id,data); }
    @DeleteMapping("/cart") public CartView clear(Principal user,@RequestParam long version) { return carts.clear(owner(user),version); }
    @PostMapping("/cart/merge") public CartView merge(Principal user,@Valid @RequestBody Merge data) { return carts.merge(owner(user),data); }
    @PostMapping("/orders") @ResponseStatus(HttpStatus.CREATED) public OrderView checkout(Principal user,@Valid @RequestBody Checkout data) { return orders.checkout(owner(user),data); }
    @GetMapping("/orders") public List<OrderView> mine(Principal user) { return orders.mine(owner(user)); }
    @GetMapping("/orders/{id}") public OrderView get(Principal user,@PathVariable Long id) { return orders.get(owner(user),id); }
    @GetMapping("/payment-options") public Map<String,Object> options() { return orders.options(); }
    @PostMapping("/orders/{id}/payments") @ResponseStatus(HttpStatus.CREATED) public PaymentView payment(Principal user,@PathVariable Long id,@Valid @RequestBody PaymentInput data) { return orders.createPayment(owner(user),id,data); }
    @PostMapping("/orders/{id}/payments/{paymentId}/confirm") public OrderView confirm(Principal user,@PathVariable Long id,@PathVariable UUID paymentId,@Valid @RequestBody(required=false) Confirmation ignored) { return orders.confirm(owner(user),id,paymentId); }
    @GetMapping("/admin/orders") @PreAuthorize("hasAnyRole('ADMINISTRADOR','VENDEDOR')") public List<OrderView> managed() { return orders.all(); }
    @GetMapping("/admin/orders/{id}") @PreAuthorize("hasAnyRole('ADMINISTRADOR','VENDEDOR')") public OrderView managed(@PathVariable Long id) { return orders.managed(id); }
    @PutMapping("/admin/orders/{id}/status") @PreAuthorize("hasRole('ADMINISTRADOR')") public OrderView transition(Principal actor,@PathVariable Long id,@Valid @RequestBody Transition data) { return orders.transition(owner(actor),id,data); }
}
