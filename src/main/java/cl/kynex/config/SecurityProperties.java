package cl.kynex.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("kynex.security")
public record SecurityProperties(String jwtSecret, String issuer, Duration accessDuration,
        Duration refreshDuration, boolean cookieSecure, List<String> allowedOrigins) {
    public SecurityProperties {
        if (jwtSecret == null || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("JWT_SECRET debe contener al menos 32 bytes.");
    }
}
