package cl.kynex.catalog.model;

import jakarta.persistence.*;

@Entity
public class Category {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public String name;
    public boolean active = true;
    @Version public Long version;
}
