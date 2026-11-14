package cl.kynex.controller;

import cl.kynex.dto.CatalogDtos.*;
import cl.kynex.service.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Consulta pública; solo Administración puede modificar el catálogo. */
@RestController
@RequestMapping("/api/v1")
@Validated
public class CatalogController {
    private final CatalogService catalog;
    public CatalogController(CatalogService catalog) { this.catalog = catalog; }
    @GetMapping("/categories") public List<CategoryView> categories() { return catalog.categories(); }
    @GetMapping("/products") public List<Map<String,Object>> products() { return catalog.products(); }
    @GetMapping("/products/{id}") public Map<String,Object> product(@PathVariable @Positive Long id) { return catalog.product(id); }

    @PostMapping("/categories") @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<CategoryView> createCategory(@Valid @RequestBody CategoryInput data) {
        var result = catalog.createCategory(data); return ResponseEntity.created(URI.create("/api/v1/categories/" + result.id())).body(result);
    }
    @PutMapping("/categories/{id}") @PreAuthorize("hasRole('ADMINISTRADOR')")
    public CategoryView updateCategory(@PathVariable @Positive Long id, @Valid @RequestBody CategoryInput data) { return catalog.updateCategory(id, data); }
    @DeleteMapping("/categories/{id}") @PreAuthorize("hasRole('ADMINISTRADOR')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable @Positive Long id, @RequestParam Long version) { catalog.deleteCategory(id, version); }

    @PostMapping("/products") @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Map<String,Object>> createProduct(@Valid @RequestBody ProductInput data) {
        var result = catalog.createProduct(data); return ResponseEntity.created(URI.create("/api/v1/products/" + result.get("id"))).body(result);
    }
    @PutMapping("/products/{id}") @PreAuthorize("hasRole('ADMINISTRADOR')")
    public Map<String,Object> updateProduct(@PathVariable @Positive Long id, @Valid @RequestBody ProductInput data) { return catalog.updateProduct(id, data); }
    @DeleteMapping("/products/{id}") @PreAuthorize("hasRole('ADMINISTRADOR')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable @Positive Long id, @RequestParam Long version) { catalog.deleteProduct(id, version); }
}
