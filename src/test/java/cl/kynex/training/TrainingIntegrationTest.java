package cl.kynex.training;

import cl.kynex.identity.model.AppUser;
import cl.kynex.identity.model.Role;
import cl.kynex.identity.repository.UserRepository;
import cl.kynex.identity.service.AuthService;
import cl.kynex.identity.service.UserService;
import cl.kynex.support.TestDatabaseGuard;
import cl.kynex.training.repository.ExerciseRepository;
import cl.kynex.training.repository.TrainingRoutineRepository;
import cl.kynex.training.service.TrainingService;
import cl.kynex.training.dto.TrainingDtos.*;
import cl.kynex.identity.dto.AuthDtos;
import cl.kynex.shared.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@ContextConfiguration(initializers=TestDatabaseGuard.class) @Transactional
class TrainingIntegrationTest {
    @Autowired TrainingService training;
    @Autowired UserService accounts;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired ExerciseRepository exercises;
    @Autowired TrainingRoutineRepository routines;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    static final String PASSWORD="ClaveRutinas2026!",HASH=new BCryptPasswordEncoder(4).encode(PASSWORD);
    Long admin,owner,other;
    @BeforeEach void setup(){admin=user(Role.ADMINISTRADOR);owner=user(Role.CLIENTE);other=user(Role.CLIENTE);}
    Long user(Role role){var u=new AppUser();u.firstName="Prueba";u.lastName="Entrenamiento";u.email=UUID.randomUUID()+"@example.com";u.role=role;u.passwordHash=HASH;return users.saveAndFlush(u).id;}
    String token(Long id){return "Bearer "+auth.login(new AuthDtos.Login(users.findById(id).orElseThrow().email,PASSWORD)).body().accessToken();}
    String body(Object value)throws Exception{return json.writeValueAsString(value);}
    RoutineView routine(Long user){return training.createRoutine(user,new RoutineName("Fuerza",null));}
    ExerciseInput exercise(String name,Long version){return new ExerciseInput(name,"Descripción del ejercicio","Pecho","Intermedio","Barra","","bi-activity",3,12,version);}
    @Test void publicLibraryPreservesEightOriginalExercisesAndSuggestions()throws Exception{
        http.perform(get("/api/v1/exercises")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(8))
            .andExpect(jsonPath("$[0].name").value("Press de banca")).andExpect(jsonPath("$[0].defaultSets").value(4)).andExpect(jsonPath("$[5].defaultReps").value(30));
        http.perform(get("/api/v1/exercises/2")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Sentadilla"));
    }
    @Test void trainingImageIsServedIndependentlyFromReact()throws Exception{
        http.perform(get("/media/training/press-banca.jpg")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }
    @Test void administratorCanCreateEditDeactivateAndReactivateExercise()throws Exception{
        var t=token(admin);var response=http.perform(post("/api/v1/admin/exercises").header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content(body(exercise("Nuevo ejercicio",null))))
            .andExpect(status().isCreated()).andReturn().getResponse();long id=json.readTree(response.getContentAsString()).get("id").asLong();
        http.perform(put("/api/v1/admin/exercises/"+id).header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content(body(exercise("Ejercicio corregido",0L)))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        http.perform(delete("/api/v1/admin/exercises/"+id+"?version=1").header("Authorization",t)).andExpect(status().isNoContent());
        http.perform(get("/api/v1/exercises/"+id)).andExpect(status().isNotFound());
        http.perform(get("/api/v1/admin/exercises/"+id).header("Authorization",t)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        http.perform(put("/api/v1/admin/exercises/"+id+"/activation").header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }
    @Test void clientsAndSellersCannotManageExercises()throws Exception{
        for(var id:List.of(owner,user(Role.VENDEDOR))){var t=token(id);
            http.perform(get("/api/v1/admin/exercises").header("Authorization",t)).andExpect(status().isForbidden());
            http.perform(post("/api/v1/admin/exercises").header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content(body(exercise("No permitido",null)))).andExpect(status().isForbidden());
            http.perform(put("/api/v1/admin/exercises/1").header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content(body(exercise("No permitido",0L)))).andExpect(status().isForbidden());
            http.perform(delete("/api/v1/admin/exercises/1?version=0").header("Authorization",t)).andExpect(status().isForbidden());
            http.perform(put("/api/v1/admin/exercises/1/activation").header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isForbidden());
        }
    }
    @Test void exerciseNamesAreUniqueAcrossAccentedCase()throws Exception{
        http.perform(post("/api/v1/admin/exercises").header("Authorization",token(admin)).contentType(MediaType.APPLICATION_JSON).content(body(exercise("CURL DE BÍCEPS",null)))).andExpect(status().isConflict());
    }
    @Test void staleExerciseMutationDoesNotOverwriteCatalog(){
        training.update(admin,1L,exercise("Press actualizado",0L));
        assertThatThrownBy(()->training.deactivate(admin,1L,0L)).isInstanceOf(ApiException.class);assertThat(training.publicExercise(1L).name()).isEqualTo("Press actualizado");
    }
    @Test void invalidPrescriptionAndImageAreRejectedByApi()throws Exception{
        var t=token(admin);
        var invalid=new ExerciseInput("Inválido","Descripción","Pecho","Otra","Barra","","bi-activity",21,0,null);
        http.perform(post("/api/v1/admin/exercises").header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content(body(invalid))).andExpect(status().isBadRequest());
        var image=new ExerciseInput("Imagen inválida","Descripción","Pecho","Principiante","Barra","https://user:pass@example.com/a.jpg","bi-activity",3,10,null);
        http.perform(post("/api/v1/admin/exercises").header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content(body(image))).andExpect(status().isBadRequest());
    }
    @Test void routineCreationAndListingArePrivate(){
        var first=routine(owner);routine(other);assertThat(training.list(owner)).hasSize(1);assertThat(training.list(owner).getFirst().id()).isEqualTo(first.id());
        assertThat(training.get(owner,first.id()).items()).isEmpty();
    }
    @Test void routineNamesAreUniqueOnlyInsideTheirOwner()throws Exception{
        routine(owner);routine(other);
        http.perform(post("/api/v1/routines").header("Authorization",token(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"fUeRzA\"}")).andExpect(status().isConflict());
    }
    @Test void routineOwnerCannotBeChosenThroughJson()throws Exception{
        http.perform(post("/api/v1/routines").header("Authorization",token(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ajena\",\"ownerId\":"+other+"}")).andExpect(status().isBadRequest());
    }
    @Test void addingExerciseUsesServerSuggestionsAndRejectsDuplicate(){
        var r=routine(owner);var saved=training.add(owner,r.id(),1L,0L);assertThat(saved.items().getFirst().sets()).isEqualTo(4);assertThat(saved.items().getFirst().reps()).isEqualTo(10);
        assertThatThrownBy(()->training.add(owner,r.id(),1L,1L)).isInstanceOf(ApiException.class);assertThat(training.get(owner,r.id()).items()).hasSize(1);
    }
    @Test void seriesAndRepetitionsPersistWithoutChangingExerciseDefaults(){
        var r=routine(owner);training.add(owner,r.id(),1L,0L);var saved=training.prescription(owner,r.id(),1L,new Prescription(2,8,1L));
        assertThat(saved.version()).isEqualTo(2);assertThat(training.get(owner,r.id()).items().getFirst().sets()).isEqualTo(2);assertThat(training.publicExercise(1L).defaultSets()).isEqualTo(4);
    }
    @Test void apiRejectsFractionsAndValuesOutsideRoutineLimits()throws Exception{
        var r=routine(owner);training.add(owner,r.id(),1L,0L);var t=token(owner);
        for(var input:List.of("{\"sets\":1.5,\"reps\":10,\"version\":1}","{\"sets\":21,\"reps\":10,\"version\":1}","{\"sets\":3,\"reps\":101,\"version\":1}","{\"sets\":0,\"reps\":10,\"version\":1}"))
            http.perform(put("/api/v1/routines/"+r.id()+"/items/1").header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isBadRequest());
        assertThat(training.get(owner,r.id()).version()).isEqualTo(1);
    }
    @Test void editingRoutineRejectsStaleVersion(){
        var r=routine(owner);training.add(owner,r.id(),1L,0L);
        assertThatThrownBy(()->training.rename(owner,r.id(),new RoutineName("Desactualizada",0L))).isInstanceOf(ApiException.class);
        assertThat(training.get(owner,r.id()).name()).isEqualTo("Fuerza");
    }
    @Test void getEditDeleteAndItemOperationsRejectAnotherOwner()throws Exception{
        var r=routine(owner);training.add(owner,r.id(),1L,0L);var t=token(other);var base="/api/v1/routines/"+r.id();
        http.perform(get(base).header("Authorization",t)).andExpect(status().isNotFound());
        http.perform(put(base).header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ajena\",\"version\":1}")).andExpect(status().isNotFound());
        http.perform(delete(base+"?version=1").header("Authorization",t)).andExpect(status().isNotFound());
        http.perform(post(base+"/items/2").header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}")).andExpect(status().isNotFound());
        http.perform(put(base+"/items/1").header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content("{\"sets\":5,\"reps\":10,\"version\":1}")).andExpect(status().isNotFound());
        http.perform(delete(base+"/items/1?version=1").header("Authorization",t)).andExpect(status().isNotFound());
        http.perform(delete(base+"/items?version=1").header("Authorization",t)).andExpect(status().isNotFound());
    }
    @Test void deactivatedExerciseRemainsInRoutineButCannotBeAddedElsewhere(){
        var r=routine(owner);training.add(owner,r.id(),1L,0L);training.deactivate(admin,1L,0);
        assertThat(training.get(owner,r.id()).items().getFirst().exercise().active()).isFalse();
        var second=training.createRoutine(owner,new RoutineName("Segunda",null));assertThatThrownBy(()->training.add(owner,second.id(),1L,0)).isInstanceOf(ApiException.class);
    }
    @Test void catalogEditsDoNotReplacePersonalPrescription(){
        var r=routine(owner);training.add(owner,r.id(),1L,0);training.prescription(owner,r.id(),1L,new Prescription(2,8,1L));training.update(admin,1L,exercise("Nombre actualizado",0L));
        var item=training.get(owner,r.id()).items().getFirst();assertThat(item.exercise().name()).isEqualTo("Nombre actualizado");assertThat(item.sets()).isEqualTo(2);assertThat(item.reps()).isEqualTo(8);
    }
    @Test void clearKeepsRoutineWhileDeleteKeepsOtherRoutinesAndCatalog(){
        var first=routine(owner);var second=training.createRoutine(owner,new RoutineName("Segunda",null));training.add(owner,first.id(),1L,0);
        training.clear(owner,first.id(),1);assertThat(training.get(owner,first.id()).items()).isEmpty();training.delete(owner,first.id(),2);
        assertThat(training.list(owner)).extracting(RoutineSummary::id).containsExactly(second.id());assertThat(training.library()).hasSize(8);
    }
    @Test void removingOneExerciseKeepsTheRest(){
        var r=routine(owner);training.add(owner,r.id(),1L,0);training.add(owner,r.id(),2L,1);assertThat(training.remove(owner,r.id(),1L,2).items()).extracting(ItemView::id).containsExactly(2L);
    }
    @Test void accountDeactivationKeepsItsRoutines(){
        var r=routine(owner);training.add(owner,r.id(),1L,0);accounts.deactivate(admin,owner,0L);accounts.activate(admin,owner,1L);
        assertThat(training.get(owner,r.id()).items()).hasSize(1);
    }
    @Test void anonymousVisitorCannotReadOrCreateRoutines()throws Exception{
        http.perform(get("/api/v1/routines")).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/routines").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Privada\"}")).andExpect(status().isUnauthorized());
    }
    @Test void routineCountIsBounded(){
        for(int i=0;i<20;i++)training.createRoutine(owner,new RoutineName("Rutina "+i,null));
        assertThatThrownBy(()->training.createRoutine(owner,new RoutineName("Exceso",null))).isInstanceOf(ApiException.class);
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentWritesDoNotOverwriteTheSameRoutine()throws Exception{
        var r=routine(owner);training.add(owner,r.id(),1L,0);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            var futures=List.of(pool.submit(()->{start.await();try{training.prescription(owner,r.id(),1L,new Prescription(5,10,1L));return true;}catch(ApiException expected){return false;}}),
                pool.submit(()->{start.await();try{training.prescription(owner,r.id(),1L,new Prescription(6,12,1L));return true;}catch(ApiException expected){return false;}}));
            start.countDown();int success=0;for(var future:futures)if(future.get(10,TimeUnit.SECONDS))success++;assertThat(success).isEqualTo(1);
            assertThat(training.get(owner,r.id()).version()).isEqualTo(2);
        }finally{pool.shutdownNow();jdbc.update("delete from training_routine where owner_id in (?,?,?)",admin,owner,other);jdbc.update("delete from app_user where id in (?,?,?)",admin,owner,other);}
    }
}
