package cl.kynex.catalog.dto;

import cl.kynex.catalog.model.Category;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

public final class CatalogDtos {
    private CatalogDtos() {}
    public record CategoryInput(@NotBlank @Size(max=80) String name, @PositiveOrZero Long version) {}
    public record CategoryView(Long id, String name, Long version) {
        public static CategoryView from(Category category) { return new CategoryView(category.id, category.name, category.version); }
    }
    public record ProductInput(@NotBlank @Size(min=3,max=30) String code, @NotNull @Positive Long categoryId,
        @NotBlank @Size(max=100) String name, @NotNull @Size(max=500) String description,
        @NotNull @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal price,
        @NotNull @Min(0) Integer stock, @Min(0) Integer criticalStock,
        @Size(max=1000) String image, boolean featured, @Size(max=20) Map<String,Object> details,
        @PositiveOrZero Long version) {}
}
