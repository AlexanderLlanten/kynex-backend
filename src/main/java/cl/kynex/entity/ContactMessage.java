package cl.kynex.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Consulta recibida. El contenido del remitente permanece separado de las notas internas. */
@Entity
public class ContactMessage {
    public enum Status { PENDIENTE, EN_ATENCION, RESUELTA }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public UUID submissionKey;
    public Long ownerId;
    public String name,email,comment,note;
    @Enumerated(EnumType.STRING) public Status status;
    @Version public Long version;
    public Instant createdAt,updatedAt;
}
