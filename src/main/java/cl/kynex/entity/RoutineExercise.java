package cl.kynex.entity;
import jakarta.persistence.*;

@Entity
public class RoutineExercise {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="routine_id") public TrainingRoutine routine;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="exercise_id") public Exercise exercise;
    public int sets,reps;
}
