package cl.kynex.training.model;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

/** La identidad propietaria es inmutable desde la API y sus escrituras se serializan por cuenta. */
@Entity
public class TrainingRoutine {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public Long ownerId;
    public String name;
    public long version=0;
    public Instant createdAt,updatedAt;
    @OneToMany(mappedBy="routine",cascade=CascadeType.ALL,orphanRemoval=true)
    @OrderBy("id asc") public List<RoutineExercise> items=new ArrayList<>();
}
