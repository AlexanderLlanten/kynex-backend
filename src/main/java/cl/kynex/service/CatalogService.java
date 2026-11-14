package cl.kynex.service;

import cl.kynex.dto.CatalogDtos.*;
import cl.kynex.entity.*;
import cl.kynex.exception.ApiException;
import cl.kynex.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reglas de catálogo compartidas por los controladores y las pruebas. */
@Service
@Transactional(readOnly=true)
public class CatalogService {
    private static final Set<String> DETAILS = Set.of("slug", "brand", "format", "flavor", "goals", "dietaryTags", "allergens", "ingredients", "benefits", "usage", "nutrition", "certifications", "warning", "icon");
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ObjectMapper json;
    public CatalogService(CategoryRepository categories, ProductRepository products, ObjectMapper json) {
        this.categories = categories; this.products = products; this.json = json;
    }
    public List<CategoryView> categories() { return categories.findByActiveTrueOrderById().stream().map(CategoryView::from).toList(); }
    public List<Map<String,Object>> products() { return products.findByActiveTrueAndCategoryActiveTrueOrderById().stream().map(this::view).toList(); }
    public Map<String,Object> product(Long id) { return view(findProduct(id)); }

    @Transactional public CategoryView createCategory(CategoryInput data) {
        var category = new Category(); category.name = name(data.name()); return CategoryView.from(categories.saveAndFlush(category));
    }
    @Transactional public CategoryView updateCategory(Long id, CategoryInput data) {
        var category = findCategory(id); checkVersion(category.version, data.version()); category.name = name(data.name());
        return CategoryView.from(categories.saveAndFlush(category));
    }
    @Transactional public void deleteCategory(Long id, Long version) {
        var category = findCategory(id); checkVersion(category.version, version);
        if (products.existsByCategoryIdAndActiveTrue(id)) throw ApiException.conflict("La categoría todavía tiene productos activos.");
        category.active = false; categories.saveAndFlush(category);
    }
    @Transactional public Map<String,Object> createProduct(ProductInput data) {
        var product = new Product(); apply(product, data); return view(products.saveAndFlush(product));
    }
    @Transactional public Map<String,Object> updateProduct(Long id, ProductInput data) {
        var product = findProduct(id); checkVersion(product.version, data.version()); apply(product, data);
        return view(products.saveAndFlush(product));
    }
    @Transactional public void deleteProduct(Long id, Long version) {
        var product = findProduct(id); checkVersion(product.version, version);
        product.active = false; products.saveAndFlush(product); // Conserva las futuras referencias comerciales.
    }
    private Category findCategory(Long id) { return categories.findById(id).filter(value -> value.active).orElseThrow(ApiException::missing); }
    private Product findProduct(Long id) { return products.findById(id).filter(value -> value.active && value.category.active).orElseThrow(ApiException::missing); }
    private static void checkVersion(Long current, Long expected) {
        if (expected == null || !expected.equals(current)) throw ApiException.conflict("El recurso cambió. Recarga antes de guardar.");
    }
    private static String name(String value) {
        String trimmed = value.trim(); if (trimmed.isEmpty()) throw ApiException.invalid("El nombre es obligatorio."); return trimmed;
    }
    private void apply(Product product, ProductInput data) {
        product.category = findCategory(data.categoryId()); product.code = data.code().trim().toUpperCase(Locale.ROOT);
        if (!product.code.matches("[A-Z0-9_-]{3,30}")) throw ApiException.invalid("Usa un código de 3 a 30 letras, números, guiones o guiones bajos.");
        product.name = name(data.name()); product.description = data.description().trim(); product.price = data.price();
        product.stock = data.stock(); product.criticalStock = data.criticalStock(); product.featured = data.featured();
        product.image = validImage(data.image());
        if (data.details() != null) {
            if (!DETAILS.containsAll(data.details().keySet())) throw ApiException.invalid("Los metadatos contienen campos no admitidos.");
            try { if (json.writeValueAsString(data.details()).length() > 12000) throw ApiException.invalid("Los metadatos son demasiado extensos."); }
            catch (JsonProcessingException ex) { throw ApiException.invalid("Los metadatos no son válidos."); }
            product.details = new LinkedHashMap<>(data.details());
        }
    }
    private static String validImage(String value) {
        if (value == null || value.isBlank()) return "";
        value = value.trim();
        if (value.matches("/media/products/[a-zA-Z0-9._-]+\\.(png|jpg|jpeg|webp)")) return value;
        try {
            var uri = URI.create(value);
            if (Set.of("https", "http").contains(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null) return value;
        } catch (IllegalArgumentException ignored) {}
        throw ApiException.invalid("La imagen debe ser una URL http(s) o un recurso de /media/products/.");
    }
    private Map<String,Object> view(Product product) {
        Map<String,Object> result = new LinkedHashMap<>(product.details);
        result.put("id", product.id); result.put("code", product.code); result.put("name", product.name);
        result.put("description", product.description); result.put("category", product.category.name); result.put("categoryId", product.category.id);
        result.put("price", product.price); result.put("stock", product.stock); result.put("criticalStock", product.criticalStock);
        result.put("image", product.image); result.put("featured", product.featured); result.put("version", product.version);
        return result;
    }
}
