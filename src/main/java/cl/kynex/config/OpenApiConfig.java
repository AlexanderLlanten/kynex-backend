package cl.kynex.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;
import org.springdoc.core.customizers.OpenApiCustomizer;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.media.StringSchema;

@Configuration
public class OpenApiConfig {
    @Bean OpenAPI openApi() {
        return new OpenAPI().info(new Info().title("KYNEX API").version("v1")
            .description("Catálogo e identidad. Las escrituras requieren JWT y rol Administrador. La autenticación con cookies requiere X-XSRF-TOKEN obtenido en /api/v1/auth/csrf."))
            .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean OpenApiCustomizer csrfDocumentation() {
        return document -> document.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            if ((path.startsWith("/api/v1/auth/") && !path.endsWith("/me"))
                    || (method == PathItem.HttpMethod.GET && (path.startsWith("/api/v1/products") || path.startsWith("/api/v1/categories"))))
                operation.setSecurity(java.util.List.of());
            if (path.startsWith("/api/v1/auth/") && method == PathItem.HttpMethod.POST)
                operation.addParametersItem(new Parameter().in("header").name("X-XSRF-TOKEN").required(true)
                    .description("Token recibido en GET /api/v1/auth/csrf; conservar también su cookie.").schema(new StringSchema()));
        }));
    }
}
