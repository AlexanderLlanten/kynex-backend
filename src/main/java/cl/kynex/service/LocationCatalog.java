package cl.kynex.service;

import cl.kynex.exception.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Catálogo de ubicaciones admitidas por los formularios de KYNEX. */
@Component
public class LocationCatalog {
    public record Location(String name, List<String> communes) {}
    private final List<Location> locations;
    public LocationCatalog(ObjectMapper mapper) throws IOException {
        try (var stream = new ClassPathResource("data/locations.json").getInputStream()) {
            locations = mapper.readValue(stream, new TypeReference<List<Location>>() {});
        }
    }
    public void validate(String region, String commune) {
        if (locations.stream().noneMatch(item -> item.name().equals(region) && item.communes().contains(commune)))
            throw ApiException.invalid("Selecciona una región y una comuna relacionadas.");
    }
}
