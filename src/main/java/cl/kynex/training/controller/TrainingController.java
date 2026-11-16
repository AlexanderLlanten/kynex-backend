package cl.kynex.training.controller;
import cl.kynex.training.dto.TrainingDtos.*;
import cl.kynex.training.service.TrainingService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class TrainingController {
    private final TrainingService training;
    public TrainingController(TrainingService training) { this.training=training; }
    private Long owner(Principal p) { return Long.valueOf(p.getName()); }
    @GetMapping("/exercises") public List<ExerciseView> library() { return training.library(); }
    @GetMapping("/exercises/{id}") public ExerciseView exercise(@PathVariable Long id) { return training.publicExercise(id); }
    @GetMapping("/admin/exercises") @PreAuthorize("hasRole('ADMINISTRADOR')") public List<ExerciseView> all(Principal p) { return training.managed(owner(p)); }
    @GetMapping("/admin/exercises/{id}") @PreAuthorize("hasRole('ADMINISTRADOR')") public ExerciseView get(Principal p,@PathVariable Long id) { return training.managed(owner(p),id); }
    @PostMapping("/admin/exercises") @PreAuthorize("hasRole('ADMINISTRADOR')") @ResponseStatus(HttpStatus.CREATED)
    public ExerciseView create(Principal p,@Valid @RequestBody ExerciseInput data) { return training.create(owner(p),data); }
    @PutMapping("/admin/exercises/{id}") @PreAuthorize("hasRole('ADMINISTRADOR')") public ExerciseView update(Principal p,@PathVariable Long id,@Valid @RequestBody ExerciseInput data) { return training.update(owner(p),id,data); }
    @DeleteMapping("/admin/exercises/{id}") @PreAuthorize("hasRole('ADMINISTRADOR')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(Principal p,@PathVariable Long id,@RequestParam long version) { training.deactivate(owner(p),id,version); }
    @PutMapping("/admin/exercises/{id}/activation") @PreAuthorize("hasRole('ADMINISTRADOR')") public ExerciseView activate(Principal p,@PathVariable Long id,@Valid @RequestBody Version data) { return training.activate(owner(p),id,data.version()); }
    @GetMapping("/routines") public List<RoutineSummary> routines(Principal p) { return training.list(owner(p)); }
    @PostMapping("/routines") @ResponseStatus(HttpStatus.CREATED) public RoutineView create(Principal p,@Valid @RequestBody RoutineName data) { return training.createRoutine(owner(p),data); }
    @GetMapping("/routines/{id}") public RoutineView routine(Principal p,@PathVariable Long id) { return training.get(owner(p),id); }
    @PutMapping("/routines/{id}") public RoutineView rename(Principal p,@PathVariable Long id,@Valid @RequestBody RoutineName data) { return training.rename(owner(p),id,data); }
    @DeleteMapping("/routines/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(Principal p,@PathVariable Long id,@RequestParam long version) { training.delete(owner(p),id,version); }
    @DeleteMapping("/routines/{id}/items") public RoutineView clear(Principal p,@PathVariable Long id,@RequestParam long version) { return training.clear(owner(p),id,version); }
    @PostMapping("/routines/{id}/items/{exerciseId}") public RoutineView add(Principal p,@PathVariable Long id,@PathVariable Long exerciseId,@Valid @RequestBody Version data) { return training.add(owner(p),id,exerciseId,data.version()); }
    @PutMapping("/routines/{id}/items/{exerciseId}") public RoutineView prescription(Principal p,@PathVariable Long id,@PathVariable Long exerciseId,@Valid @RequestBody Prescription data) { return training.prescription(owner(p),id,exerciseId,data); }
    @DeleteMapping("/routines/{id}/items/{exerciseId}") public RoutineView remove(Principal p,@PathVariable Long id,@PathVariable Long exerciseId,@RequestParam long version) { return training.remove(owner(p),id,exerciseId,version); }
}
