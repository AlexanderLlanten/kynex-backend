package cl.kynex.catalog.service;

import cl.kynex.catalog.api.InventoryAccess;
import cl.kynex.catalog.model.Product;
import cl.kynex.catalog.repository.ProductRepository;
import cl.kynex.shared.exception.ApiException;
import java.util.Collection;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class InventoryAccessService implements InventoryAccess {
    private final ProductRepository products;

    public InventoryAccessService(ProductRepository products) { this.products = products; }

    @Override public Optional<Product> find(Long id) { return products.findById(id); }

    @Override public SortedMap<Long, Product> lock(Collection<Long> ids) {
        var result = new TreeMap<Long, Product>();
        // El mismo orden entre cuentas evita ciclos al comprar los mismos productos.
        for (var id : new TreeSet<>(ids))
            result.put(id, Hibernate.unproxy(products.lockById(id).orElseThrow(ApiException::missing), Product.class));
        return result;
    }
}
