package cl.kynex.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Guarda solamente el verificador del token opaco de renovación. */
@Entity
public class RefreshSession {
    @Id public UUID id = UUID.randomUUID();
    public UUID family;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) public AppUser user;
    public String tokenHash;
    public Instant expiresAt;
    public Instant consumedAt;
    public boolean revoked;
}
