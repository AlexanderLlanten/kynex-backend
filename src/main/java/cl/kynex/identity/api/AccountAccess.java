package cl.kynex.identity.api;

import java.util.Optional;

/** Contrato interno de identidad para otros módulos; no expone entidades ni credenciales. */
public interface AccountAccess {
    record Account(Long id, String firstName, String lastName, String email) {}

    /** Conserva el bloqueo hasta finalizar la transacción del caso de uso. */
    Account lockActive(Long owner);

    /** Permite gestionar pedidos históricos aunque su propietario haya sido desactivado. */
    void lockExisting(Long owner);

    /** Coordina la administración y comprueba el rol vigente en la misma transacción. */
    Account requireAdministrator(Long actor);

    Optional<Account> findActiveByEmail(String email);
}
