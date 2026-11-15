package cl.kynex.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Respuesta publicada para el propietario; se almacena aparte de las notas internas. */
@Entity
public class ContactReply {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public Long messageId,actorId;
    public UUID replyKey;
    public String actorName,text;
    public Instant createdAt;
}
