package cl.kynex.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/** Identidad persistente. El hash nunca se entrega en las respuestas de la API. */
@Entity
@Table(name = "app_user")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String run;
    public String firstName;
    public String lastName;
    public String email;
    public LocalDate birthDate;
    public String region;
    public String commune;
    public String address;
    public String passwordHash;
    @Enumerated(EnumType.STRING) public Role role = Role.CLIENTE;
    public boolean active = true;
    public Instant createdAt = Instant.now();
    @Version public Long version = 0L;
}
