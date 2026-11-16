package cl.kynex.training.service;

import cl.kynex.identity.api.AccountAccess;
import cl.kynex.training.model.Exercise;
import cl.kynex.training.model.RoutineExercise;
import cl.kynex.training.model.TrainingRoutine;
import cl.kynex.training.repository.ExerciseRepository;
import cl.kynex.training.repository.TrainingRoutineRepository;
import cl.kynex.training.dto.TrainingDtos.*;
import cl.kynex.shared.exception.ApiException;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entrenamiento público y rutinas privadas con versiones, límites y bajas sin pérdida de referencias. */
@Service @Transactional
public class TrainingService {
    private final ExerciseRepository exercises;
    private final TrainingRoutineRepository routines;
    private final AccountAccess accounts;
    public TrainingService(ExerciseRepository exercises,TrainingRoutineRepository routines,AccountAccess accounts) {
        this.exercises=exercises;this.routines=routines;this.accounts=accounts;
    }
    private Exercise exercise(Long id) { return exercises.findById(id).orElseThrow(ApiException::missing); }
    private Exercise lockedExercise(Long id) { return Hibernate.unproxy(exercises.lockById(id).orElseThrow(ApiException::missing),Exercise.class); }
    private void admin(Long actor) {
        accounts.requireAdministrator(actor);
    }
    private void version(long current,Long expected) { if (expected==null || current!=expected) throw ApiException.conflict("Los datos cambiaron. Recarga antes de guardar."); }
    @Transactional(readOnly=true) public List<ExerciseView> library() { return exercises.findByActiveTrueOrderById().stream().map(ExerciseView::from).toList(); }
    @Transactional(readOnly=true) public ExerciseView publicExercise(Long id) { var e=exercise(id);if(!e.active)throw ApiException.missing();return ExerciseView.from(e); }
    public List<ExerciseView> managed(Long actor) { admin(actor);return exercises.findAllByOrderById().stream().map(ExerciseView::from).toList(); }
    public ExerciseView managed(Long actor,Long id) { admin(actor);return ExerciseView.from(exercise(id)); }
    public ExerciseView create(Long actor,ExerciseInput data) { admin(actor);var e=new Exercise();apply(e,data);return ExerciseView.from(exercises.saveAndFlush(e)); }
    public ExerciseView update(Long actor,Long id,ExerciseInput data) { admin(actor);var e=lockedExercise(id);version(e.version,data.version());apply(e,data);return ExerciseView.from(exercises.saveAndFlush(e)); }
    public void deactivate(Long actor,Long id,long expected) { admin(actor);var e=lockedExercise(id);version(e.version,expected);e.active=false;exercises.saveAndFlush(e); }
    public ExerciseView activate(Long actor,Long id,long expected) { admin(actor);var e=lockedExercise(id);version(e.version,expected);e.active=true;return ExerciseView.from(exercises.saveAndFlush(e)); }
    private void apply(Exercise e,ExerciseInput data) {
        e.name=data.name().trim();e.description=data.description().trim();e.muscle=data.muscle().trim();e.equipment=data.equipment().trim();e.difficulty=data.difficulty();
        if(e.name.isEmpty()||e.description.isEmpty()||e.muscle.isEmpty()||e.equipment.isEmpty())throw ApiException.invalid("Completa los datos del ejercicio.");
        e.image=image(data.image());e.icon=data.icon()==null?"bi-activity":data.icon();e.defaultSets=data.defaultSets();e.defaultReps=data.defaultReps();
    }
    private String image(String value) {
        if(value==null||value.isBlank())return "";value=value.trim();
        if(value.matches("/media/training/[a-zA-Z0-9._-]+\\.(jpg|jpeg|png|webp)"))return value;
        try {var uri=URI.create(value);if(Set.of("http","https").contains(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null)return value;}catch(IllegalArgumentException ignored){}
        throw ApiException.invalid("La imagen debe ser una URL http(s) o un recurso de /media/training/.");
    }
    private TrainingRoutine owned(Long owner,Long id) {
        if(!routines.ownerOf(id).orElseThrow(ApiException::missing).equals(owner))throw ApiException.missing();
        accounts.lockActive(owner);return routines.findById(id).orElseThrow(ApiException::missing);
    }
    public List<RoutineSummary> list(Long owner) { accounts.lockActive(owner);return routines.findByOwnerIdOrderById(owner).stream().map(r->new RoutineSummary(r.id,r.name,r.version,r.items.size(),r.items.stream().mapToInt(i->i.sets).sum(),r.updatedAt)).toList(); }
    public RoutineView get(Long owner,Long id) { return view(owned(owner,id)); }
    public RoutineView createRoutine(Long owner,RoutineName data) {
        accounts.lockActive(owner);if(routines.countByOwnerId(owner)>=20)throw ApiException.invalid("Cada cuenta admite hasta 20 rutinas.");
        var r=new TrainingRoutine();r.ownerId=owner;r.name=name(data.name());r.createdAt=Instant.now();r.updatedAt=r.createdAt;
        return view(routines.saveAndFlush(r));
    }
    private String name(String value) { var result=value.trim();if(result.isEmpty())throw ApiException.invalid("El nombre de la rutina es obligatorio.");return result; }
    public RoutineView rename(Long owner,Long id,RoutineName data) { var r=owned(owner,id);version(r.version,data.version());r.name=name(data.name());return saved(r); }
    public void delete(Long owner,Long id,long expected) { var r=owned(owner,id);version(r.version,expected);routines.delete(r);routines.flush(); }
    public RoutineView clear(Long owner,Long id,long expected) { var r=owned(owner,id);version(r.version,expected);r.items.clear();return saved(r); }
    public RoutineView add(Long owner,Long id,Long exerciseId,long expected) {
        var r=owned(owner,id);version(r.version,expected);var e=lockedExercise(exerciseId);
        if(!e.active)throw ApiException.conflict("El ejercicio ya no está disponible para agregar.");
        if(r.items.stream().anyMatch(item->Hibernate.unproxy(item.exercise,Exercise.class).id.equals(exerciseId)))throw ApiException.conflict("El ejercicio ya está en esta rutina.");
        if(r.items.size()>=100)throw ApiException.invalid("La rutina admite hasta 100 ejercicios distintos.");
        var item=new RoutineExercise();item.routine=r;item.exercise=e;item.sets=e.defaultSets;item.reps=e.defaultReps;r.items.add(item);return saved(r);
    }
    public RoutineView prescription(Long owner,Long id,Long exerciseId,Prescription data) {
        var r=owned(owner,id);version(r.version,data.version());var item=r.items.stream().filter(i->Hibernate.unproxy(i.exercise,Exercise.class).id.equals(exerciseId)).findFirst().orElseThrow(ApiException::missing);
        item.sets=data.sets();item.reps=data.reps();return saved(r);
    }
    public RoutineView remove(Long owner,Long id,Long exerciseId,long expected) {
        var r=owned(owner,id);version(r.version,expected);
        if(!r.items.removeIf(i->Hibernate.unproxy(i.exercise,Exercise.class).id.equals(exerciseId)))throw ApiException.missing();return saved(r);
    }
    private RoutineView saved(TrainingRoutine r) { r.version++;r.updatedAt=Instant.now();return view(routines.saveAndFlush(r)); }
    private RoutineView view(TrainingRoutine r) { return new RoutineView(r.id,r.name,r.version,r.items.stream().map(i->new ItemView(Hibernate.unproxy(i.exercise,Exercise.class).id,i.sets,i.reps,ExerciseView.from(Hibernate.unproxy(i.exercise,Exercise.class)))).toList(),r.createdAt,r.updatedAt); }
}
