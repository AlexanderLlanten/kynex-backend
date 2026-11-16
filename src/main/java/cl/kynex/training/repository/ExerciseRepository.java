package cl.kynex.training.repository;
import cl.kynex.training.model.Exercise;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ExerciseRepository extends JpaRepository<Exercise,Long> {
    List<Exercise> findByActiveTrueOrderById();
    List<Exercise> findAllByOrderById();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Exercise e where e.id=:id") Optional<Exercise> lockById(Long id);
}
