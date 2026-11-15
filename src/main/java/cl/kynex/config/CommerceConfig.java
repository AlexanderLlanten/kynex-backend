package cl.kynex.config;
import java.time.Clock;
import org.springframework.context.annotation.*;
@Configuration
public class CommerceConfig {
    @Bean Clock commerceClock() { return Clock.systemUTC(); }
}
