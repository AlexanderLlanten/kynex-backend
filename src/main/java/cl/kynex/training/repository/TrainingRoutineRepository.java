package cl.kynex.training.repository;
import cl.kynex.training.model.TrainingRoutine;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface TrainingRoutineRepository extends JpaRepository<TrainingRoutine,Long> {
    List<TrainingRoutine> findByOwnerIdOrderById(Long ownerId);
    long countByOwnerId(Long ownerId);
    @Query("select r.ownerId from TrainingRoutine r where r.id=:id") Optional<Long> ownerOf(Long id);
}
