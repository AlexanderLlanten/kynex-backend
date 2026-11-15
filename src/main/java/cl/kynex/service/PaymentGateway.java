package cl.kynex.service;
import cl.kynex.entity.*;
public interface PaymentGateway {
    boolean enabled();
    PaymentStatus resolve(PaymentScenario scenario);
}
