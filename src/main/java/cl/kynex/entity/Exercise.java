package cl.kynex.entity;
import jakarta.persistence.*;

/** Catálogo compartido; la baja mantiene las referencias de rutinas existentes. */
@Entity
public class Exercise {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public String name,description,muscle,difficulty,equipment,image,icon;
    public int defaultSets,defaultReps;
    public boolean active=true;
    @Version public Long version;
}
