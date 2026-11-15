package cl.kynex.service;
import cl.kynex.entity.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Entorno académico explícito: no contacta bancos ni procesa datos de tarjetas. */
@Component
public class EmulatedPaymentGateway implements PaymentGateway {
    private final boolean enabled;
    public EmulatedPaymentGateway(@Value("${kynex.commerce.emulator-enabled:false}") boolean enabled) { this.enabled = enabled; }
    public boolean enabled() { return enabled; }
    public PaymentStatus resolve(PaymentScenario scenario) {
        return switch(scenario) {
            case APPROVE -> PaymentStatus.APPROVED; case REJECT -> PaymentStatus.REJECTED;
            case CANCEL -> PaymentStatus.CANCELLED; case EXPIRE -> PaymentStatus.EXPIRED;
            case TECHNICAL_ERROR -> PaymentStatus.ERROR;
        };
    }
}
