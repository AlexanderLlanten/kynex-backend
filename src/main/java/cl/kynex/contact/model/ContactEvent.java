package cl.kynex.contact.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Evidencia inmutable de quién registró cada cambio de atención. */
@Entity
public class ContactEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public Long messageId,actorId;
    public String actorName,note;
    @Enumerated(EnumType.STRING) public ContactMessage.Status status;
    public Instant createdAt;
}
