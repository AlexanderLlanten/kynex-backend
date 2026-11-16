package cl.kynex.catalog.api;

import cl.kynex.catalog.model.Product;
import java.util.Collection;
import java.util.Optional;
import java.util.SortedMap;

/** Acceso al inventario dentro del monolito; los productos participan en la transacción comercial. */
public interface InventoryAccess {
    Optional<Product> find(Long id);
    SortedMap<Long, Product> lock(Collection<Long> ids);
}
