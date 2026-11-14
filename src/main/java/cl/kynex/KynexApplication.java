package cl.kynex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Punto de entrada: configura Spring y levanta la API HTTP. */
@SpringBootApplication
public class KynexApplication {
    public static void main(String[] args) { SpringApplication.run(KynexApplication.class, args); }
}
