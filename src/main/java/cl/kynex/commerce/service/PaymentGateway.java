package cl.kynex.commerce.service;

import cl.kynex.commerce.model.PaymentScenario;
import cl.kynex.commerce.model.PaymentStatus;
public interface PaymentGateway {
    boolean enabled();
    PaymentStatus resolve(PaymentScenario scenario);
}
