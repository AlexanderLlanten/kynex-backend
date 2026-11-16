package cl.kynex.identity.model;
import jakarta.persistence.*;

/** Fila de coordinación de los cambios de rol y estado de cuenta. */
@Entity
public class AccountPolicy {
    @Id public Integer id;
}
