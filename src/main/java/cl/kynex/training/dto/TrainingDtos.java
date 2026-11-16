package cl.kynex.training.dto;
import cl.kynex.training.model.Exercise;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class TrainingDtos {
    private TrainingDtos() {}
    public record ExerciseInput(@NotBlank @Size(max=100) String name,@NotBlank @Size(max=1000) String description,
        @NotBlank @Size(max=60) String muscle,@NotBlank @Pattern(regexp="Principiante|Intermedio|Avanzado") String difficulty,
        @NotBlank @Size(max=100) String equipment,@Size(max=1000) String image,
        @Pattern(regexp="bi-[a-z0-9-]+") @Size(max=50) String icon,
        @NotNull @Min(1) @Max(20) Integer defaultSets,@NotNull @Min(1) @Max(100) Integer defaultReps,
        @Min(0) Long version) {}
    public record ExerciseView(Long id,String name,String description,String muscle,String difficulty,String equipment,
        String image,String icon,int defaultSets,int defaultReps,boolean active,long version) {
        public static ExerciseView from(Exercise e) { return new ExerciseView(e.id,e.name,e.description,e.muscle,e.difficulty,e.equipment,e.image,e.icon,e.defaultSets,e.defaultReps,e.active,e.version); }
    }
    public record RoutineName(@NotBlank @Size(max=100) String name,@Min(0) Long version) {}
    public record Version(@NotNull @Min(0) Long version) {}
    public record Prescription(@NotNull @Min(1) @Max(20) Integer sets,@NotNull @Min(1) @Max(100) Integer reps,@NotNull @Min(0) Long version) {}
    public record RoutineSummary(Long id,String name,long version,int totalExercises,int totalSets,Instant updatedAt) {}
    public record ItemView(Long id,int sets,int reps,ExerciseView exercise) {}
    public record RoutineView(Long id,String name,long version,List<ItemView> items,Instant createdAt,Instant updatedAt) {}
}
