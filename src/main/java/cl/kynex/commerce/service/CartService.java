package cl.kynex.commerce.service;

import cl.kynex.catalog.model.Category;
import cl.kynex.catalog.model.Product;
import cl.kynex.catalog.repository.ProductRepository;
import cl.kynex.commerce.model.CartLine;
import cl.kynex.commerce.model.CartMerge;
import cl.kynex.commerce.model.ShoppingCart;
import cl.kynex.commerce.repository.CartMergeRepository;
import cl.kynex.commerce.repository.CartRepository;
import cl.kynex.identity.model.AppUser;
import cl.kynex.identity.repository.UserRepository;
import cl.kynex.commerce.dto.CommerceDtos.*;
import cl.kynex.shared.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serializa las escrituras de una cuenta y calcula importes exclusivamente con datos del servidor. */
@Service
@Transactional
public class CartService {
    static final BigDecimal FREE_FROM = new BigDecimal("50000.00"), SHIPPING = new BigDecimal("3990.00");
    private final UserRepository users;
    private final CartRepository carts;
    private final CartMergeRepository merges;
    private final ProductRepository products;
    private final ObjectMapper json;
    public CartService(UserRepository users, CartRepository carts, CartMergeRepository merges, ProductRepository products, ObjectMapper json) {
        this.users=users; this.carts=carts; this.merges=merges; this.products=products; this.json=json;
    }
    public AppUser lockUser(Long owner) {
        var user=Hibernate.unproxy(users.lockById(owner).orElseThrow(ApiException::missing), AppUser.class);
        if (!user.active) throw new ApiException(HttpStatus.UNAUTHORIZED,"La cuenta ya no está activa.");
        return user;
    }
    public ShoppingCart cart(Long owner) {
        return carts.findById(owner).orElseGet(() -> { var cart=new ShoppingCart(); cart.userId=owner; return carts.saveAndFlush(cart); });
    }
    public CartView get(Long owner) { lockUser(owner); return view(cart(owner), List.of()); }
    public CartView quantity(Long owner, Long productId, CartQuantity data) {
        lockUser(owner); var cart=cart(owner); checkVersion(cart, data.version());
        var previous=cart.lines.stream().filter(line -> product(line).id.equals(productId)).findFirst().orElse(null);
        if (data.quantity()==0) { if (previous!=null) { cart.lines.remove(previous); cart.revision++; } }
        else {
            var product=lockedProducts(List.of(productId)).get(productId);
            if (!available(product) || data.quantity()>product.stock) throw ApiException.conflict("La cantidad supera la disponibilidad vigente.");
            if (previous==null) {
                if (cart.lines.size()>=100) throw ApiException.invalid("El carrito admite hasta 100 productos distintos.");
                previous=new CartLine(); previous.cart=cart; previous.product=product; cart.lines.add(previous);
            }
            previous.quantity=data.quantity(); cart.revision++;
        }
        carts.saveAndFlush(cart); return view(cart,List.of());
    }
    public CartView clear(Long owner,long version) {
        lockUser(owner); var cart=cart(owner); checkVersion(cart,version);
        if (!cart.lines.isEmpty()) { cart.lines.clear(); cart.revision++; }
        carts.saveAndFlush(cart); return view(cart,List.of());
    }
    public CartView merge(Long owner,Merge data) {
        lockUser(owner); var cart=cart(owner);
        var sorted=data.items().stream().sorted(Comparator.comparing(GuestLine::id)).toList();
        if (sorted.stream().map(GuestLine::id).distinct().count()!=sorted.size()) throw ApiException.invalid("El carrito contiene productos repetidos.");
        String fingerprint=fingerprint(sorted);
        var previous=merges.findByUserIdAndRequestId(owner,data.requestId());
        if (previous.isPresent()) {
            if (!previous.get().fingerprint.equals(fingerprint)) throw ApiException.conflict("La clave de conciliación ya se utilizó con otro carrito.");
            return view(cart,previous.get().warnings);
        }
        var warnings=new ArrayList<String>();
        for (var item:sorted) {
            var candidate=products.findById(item.id()).orElse(null);
            if (candidate==null) { warnings.add("Un producto del carrito visitante ya no existe."); continue; }
            var product=Hibernate.unproxy(candidate,Product.class);
            if (!available(product) || product.stock==0) { warnings.add(product.name+" ya no está disponible."); continue; }
            var line=cart.lines.stream().filter(value -> product(value).id.equals(item.id())).findFirst().orElse(null);
            if (line==null) {
                if (cart.lines.size()>=100) { warnings.add("Se alcanzó el límite de 100 productos del carrito."); continue; }
                line=new CartLine(); line.cart=cart; line.product=product; cart.lines.add(line);
            }
            long wanted=(long)line.quantity+item.quantity();
            line.quantity=(int)Math.min(wanted,product.stock);
            if (wanted>product.stock) warnings.add("La cantidad de "+product.name+" se ajustó al stock disponible.");
        }
        cart.revision++; carts.saveAndFlush(cart);
        var receipt=new CartMerge(); receipt.userId=owner; receipt.requestId=data.requestId(); receipt.fingerprint=fingerprint; receipt.warnings=List.copyOf(warnings); merges.save(receipt);
        return view(cart,warnings);
    }
    public void checkVersion(ShoppingCart cart,long expected) {
        if (cart.revision!=expected) throw ApiException.conflict("El carrito cambió. Recarga sus datos antes de continuar.");
    }
    public Product product(CartLine line) { return Hibernate.unproxy(line.product,Product.class); }
    public boolean available(Product product) { return product.active && Hibernate.unproxy(product.category,Category.class).active; }
    public SortedMap<Long,Product> lockedProducts(Collection<Long> ids) {
        var result=new TreeMap<Long,Product>();
        // Orden idéntico entre cuentas: evita ciclos de bloqueo al comprar los mismos productos.
        for (var id:new TreeSet<>(ids)) result.put(id,Hibernate.unproxy(products.lockById(id).orElseThrow(ApiException::missing),Product.class));
        return result;
    }
    public CartView view(ShoppingCart cart,List<String> initialWarnings) {
        var items=new ArrayList<Map<String,Object>>(); var warnings=new ArrayList<>(initialWarnings);
        BigDecimal subtotal=BigDecimal.ZERO; long units=0; boolean valid=!cart.lines.isEmpty();
        for (var line:cart.lines) {
            var product=product(line); boolean active=available(product);
            Map<String,Object> item=new LinkedHashMap<>(product.details);
            item.put("id",product.id); item.put("code",product.code); item.put("name",product.name); item.put("description",product.description);
            item.put("category",Hibernate.unproxy(product.category,Category.class).name); item.put("image",product.image);
            item.put("price",product.price); item.put("stock",active?product.stock:0); item.put("criticalStock",product.criticalStock); item.put("quantity",line.quantity);
            items.add(item); subtotal=subtotal.add(product.price.multiply(BigDecimal.valueOf(line.quantity))); units+=line.quantity;
            if (!active || line.quantity>product.stock) { valid=false; warnings.add("Revisa la disponibilidad y cantidad de "+product.name+"."); }
        }
        BigDecimal shipping=shipping(subtotal);
        return new CartView(cart.revision,items,units,subtotal,shipping,subtotal.add(shipping),
            subtotal.signum()>0 && subtotal.compareTo(FREE_FROM)<0 ? FREE_FROM.subtract(subtotal) : BigDecimal.ZERO,valid,List.copyOf(warnings));
    }
    public static BigDecimal shipping(BigDecimal subtotal) { return subtotal.signum()>0 && subtotal.compareTo(FREE_FROM)<0 ? SHIPPING : BigDecimal.ZERO; }
    public String fingerprint(Object data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(data).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception ex) { throw new IllegalStateException("No se pudo identificar la operación.",ex); }
    }
}
