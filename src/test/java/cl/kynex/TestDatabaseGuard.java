package cl.kynex;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Rechaza una URL ajena a pruebas antes de que Flyway pueda acceder a la base. */
public class TestDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override public void initialize(ConfigurableApplicationContext context) {
        String url = context.getEnvironment().getProperty("spring.datasource.url", "");
        if (!url.matches("jdbc:postgresql://[^/]+/[A-Za-z0-9_]+_test(?:\\?.*)?"))
            throw new IllegalArgumentException("Las pruebas requieren una base PostgreSQL cuyo nombre termine en _test.");
    }
}
