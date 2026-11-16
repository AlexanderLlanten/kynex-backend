package cl.kynex.contact;

import cl.kynex.contact.repository.ContactMessageRepository;
import cl.kynex.contact.service.ContactService;
import cl.kynex.identity.model.AppUser;
import cl.kynex.identity.model.Role;
import cl.kynex.identity.repository.UserRepository;
import cl.kynex.identity.service.AuthService;
import cl.kynex.support.TestDatabaseGuard;

import cl.kynex.identity.dto.AuthDtos;
import cl.kynex.contact.dto.ContactDtos.*;
import cl.kynex.contact.model.ContactMessage.Status;
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
class ContactIntegrationTest {
    @Autowired ContactService contact;
    @Autowired ContactMessageRepository messages;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    static final String PASSWORD="ClaveContacto2026!",HASH=new BCryptPasswordEncoder(4).encode(PASSWORD);
    Long admin,client,seller;
    @BeforeEach void setup(){admin=user(Role.ADMINISTRADOR);client=user(Role.CLIENTE);seller=user(Role.VENDEDOR);}
    Long user(Role role){var u=new AppUser();u.firstName="Prueba";u.lastName="Contacto";u.email=UUID.randomUUID()+"@gmail.com";u.role=role;u.passwordHash=HASH;return users.saveAndFlush(u).id;}
    String token(Long id){return "Bearer "+auth.login(new AuthDtos.Login(users.findById(id).orElseThrow().email,PASSWORD)).body().accessToken();}
    String body(Object data)throws Exception{return json.writeValueAsString(data);}
    Submission submission(){return new Submission(UUID.randomUUID(),"  Camila  ","  CAMILA@GMAIL.COM  ","  Consulta de productos  ");}
    Long receive(){var data=submission();contact.receive(data);return messages.findBySubmissionKey(data.submissionKey()).orElseThrow().id;}
    @Test void swaggerDocumentsPublicSubmissionAndProtectedAttention()throws Exception{
        http.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/contact'].post.security").isEmpty())
            .andExpect(jsonPath("$.paths['/api/v1/exercises'].get.security").isEmpty())
            .andExpect(jsonPath("$.security[0].bearerAuth").exists())
            .andExpect(jsonPath("$.paths['/api/v1/admin/contact/{id}'].put.responses['409']").exists());
    }
    @Test void anonymousSubmissionPersistsNormalizedDataAndReturnsOnlyReceipt()throws Exception{
        var data=submission();http.perform(post("/api/v1/contact").contentType(MediaType.APPLICATION_JSON).content(body(data)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.reference").value(data.submissionKey().toString())).andExpect(jsonPath("$.receivedAt").exists())
            .andExpect(jsonPath("$.id").doesNotExist()).andExpect(jsonPath("$.name").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.note").doesNotExist());
        var saved=messages.findBySubmissionKey(data.submissionKey()).orElseThrow();assertThat(saved.name).isEqualTo("Camila");assertThat(saved.email).isEqualTo("camila@gmail.com");assertThat(saved.comment).isEqualTo("Consulta de productos");assertThat(saved.status).isEqualTo(Status.PENDIENTE);
    }
    @Test void retryReturnsSameReceiptAndDoesNotDuplicateMessage(){
        var data=submission();long before=messages.count();var first=contact.receive(data);var id=messages.findBySubmissionKey(data.submissionKey()).orElseThrow().id;
        contact.attend(admin,id,new Attention(Status.RESUELTA,"Consulta atendida",0L));
        assertThat(contact.receive(data)).isEqualTo(first);assertThat(messages.count()).isEqualTo(before+1);assertThat(contact.detail(admin,id).status()).isEqualTo(Status.RESUELTA);
    }
    @Test void reusedReferenceWithDifferentContentIsRejected()throws Exception{
        var data=submission();contact.receive(data);
        http.perform(post("/api/v1/contact").contentType(MediaType.APPLICATION_JSON).content(body(new Submission(data.submissionKey(),"Otra persona","otra@gmail.com","Otro mensaje"))))
            .andExpect(status().isConflict());assertThat(messages.findBySubmissionKey(data.submissionKey()).orElseThrow().name).isEqualTo("Camila");
    }
    @Test void missingFieldsInvalidKeyAndWhitespaceAreRejected()throws Exception{
        for(var input:List.of("{}","{\"submissionKey\":\"inválida\",\"name\":\"Nombre\",\"email\":\"a@gmail.com\",\"comment\":\"Hola\"}",body(new Submission(UUID.randomUUID(),"  ","a@gmail.com","Consulta")),body(new Submission(UUID.randomUUID(),"Nombre","a@gmail.com","  "))))
            http.perform(post("/api/v1/contact").contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isBadRequest());
    }
    @Test void publicSubmissionEnforcesAllThreeLengthLimits()throws Exception{
        for(var data:List.of(new Submission(UUID.randomUUID(),"a".repeat(101),"a@gmail.com","Hola"),new Submission(UUID.randomUUID(),"Nombre","a".repeat(91)+"@gmail.com","Hola"),new Submission(UUID.randomUUID(),"Nombre","a@gmail.com","a".repeat(501))))
            http.perform(post("/api/v1/contact").contentType(MediaType.APPLICATION_JSON).content(body(data))).andExpect(status().isBadRequest());
    }
    @Test void emailMustUseAllowedDomainAndValidShape()throws Exception{
        for(var email:List.of("correo","a@example.com","a@@gmail.com","a b@gmail.com","a@gmail.com.evil"))
            http.perform(post("/api/v1/contact").contentType(MediaType.APPLICATION_JSON).content(body(new Submission(UUID.randomUUID(),"Nombre",email,"Consulta")))).andExpect(status().isBadRequest());
    }
    @Test void acceptedDomainsAndBoundaryValuesPersist(){
        for(var email:List.of("a@duoc.cl","a@profesor.duoc.cl","a@gmail.com"))contact.receive(new Submission(UUID.randomUUID(),"a".repeat(100),email,"b".repeat(500)));
        assertThat(contact.inbox(admin,null,0).totalElements()).isEqualTo(3);
    }
    @Test void publicPayloadCannotAssignStatusOwnerOrInternalNote()throws Exception{
        for(var field:List.of("\"status\":\"RESUELTA\"","\"note\":\"Oculta\"","\"ownerId\":1","\"id\":1")){
            var input=body(submission());input=input.substring(0,input.length()-1)+","+field+"}";
            http.perform(post("/api/v1/contact").contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isBadRequest());
        }
    }
    @Test void anonymousVisitorCannotReadInboxDetailOrUpdate()throws Exception{
        var id=receive();http.perform(get("/api/v1/admin/contact")).andExpect(status().isUnauthorized());http.perform(get("/api/v1/admin/contact/"+id)).andExpect(status().isUnauthorized());
        http.perform(put("/api/v1/admin/contact/"+id).contentType(MediaType.APPLICATION_JSON).content(body(new Attention(Status.EN_ATENCION,"Nota",0L)))).andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/contact")).andExpect(status().isUnauthorized());
    }
    @Test void clientAndSellerCannotReadOrManageContact()throws Exception{
        var id=receive();for(var actor:List.of(client,seller)){var t=token(actor);
            http.perform(get("/api/v1/admin/contact").header("Authorization",t)).andExpect(status().isForbidden());
            http.perform(get("/api/v1/admin/contact/"+id).header("Authorization",t)).andExpect(status().isForbidden());
            http.perform(put("/api/v1/admin/contact/"+id).header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content(body(new Attention(Status.RESUELTA,"Consulta atendida",0L)))).andExpect(status().isForbidden());
        }
    }
    @Test void adminReadsContentAndRecordsAttentionWithHistory()throws Exception{
        var id=receive();var t=token(admin);
        http.perform(get("/api/v1/admin/contact/"+id).header("Authorization",t)).andExpect(status().isOk()).andExpect(jsonPath("$.comment").value("Consulta de productos"));
        http.perform(put("/api/v1/admin/contact/"+id).header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content(body(new Attention(Status.EN_ATENCION,"  Se revisó el catálogo  ",0L))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.history[0].actorName").value("Prueba Contacto")).andExpect(jsonPath("$.history[0].note").value("Se revisó el catálogo"));
        var saved=contact.attend(admin,id,new Attention(Status.RESUELTA,"Consulta atendida por teléfono",1L));assertThat(saved.history()).hasSize(2);assertThat(saved.comment()).isEqualTo("Consulta de productos");
    }
    @Test void resolutionRequiresMeaningfulNote()throws Exception{
        var id=receive();http.perform(put("/api/v1/admin/contact/"+id).header("Authorization",token(admin)).contentType(MediaType.APPLICATION_JSON).content(body(new Attention(Status.RESUELTA,"  ",0L)))).andExpect(status().isBadRequest());
        assertThat(contact.detail(admin,id).status()).isEqualTo(Status.PENDIENTE);assertThat(contact.detail(admin,id).history()).isEmpty();
    }
    @Test void invalidStatusVersionNoteAndUnknownFieldsAreRejected()throws Exception{
        var id=receive();var t=token(admin);
        for(var input:List.of("{\"status\":\"OTRO\",\"note\":\"Nota\",\"version\":0}","{\"status\":\"PENDIENTE\",\"note\":\"Nota\"}","{\"status\":\"PENDIENTE\",\"note\":\"Nota\",\"version\":0.5}",body(new Attention(Status.EN_ATENCION,"a".repeat(1001),0L)),"{\"status\":\"PENDIENTE\",\"note\":\"Nota\",\"version\":0,\"comment\":\"Cambiado\"}"))
            http.perform(put("/api/v1/admin/contact/"+id).header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isBadRequest());
    }
    @Test void staleUpdateKeepsLatestAttentionAndHistory()throws Exception{
        var id=receive();contact.attend(admin,id,new Attention(Status.EN_ATENCION,"Primera atención",0L));
        http.perform(put("/api/v1/admin/contact/"+id).header("Authorization",token(admin)).contentType(MediaType.APPLICATION_JSON).content(body(new Attention(Status.RESUELTA,"Sobrescritura",0L)))).andExpect(status().isConflict());
        assertThat(contact.detail(admin,id).note()).isEqualTo("Primera atención");assertThat(contact.detail(admin,id).history()).hasSize(1);
    }
    @Test void repeatedUnchangedAttentionDoesNotDuplicateHistory(){
        var id=receive();contact.attend(admin,id,new Attention(Status.EN_ATENCION,"En revisión",0L));var saved=contact.attend(admin,id,new Attention(Status.EN_ATENCION,"En revisión",1L));
        assertThat(saved.version()).isEqualTo(1);assertThat(saved.history()).hasSize(1);
    }
    @Test void resolvedRequestCanBeReopenedWithoutChangingOriginalMessage(){
        var id=receive();contact.attend(admin,id,new Attention(Status.RESUELTA,"Resuelta",0L));var saved=contact.attend(admin,id,new Attention(Status.EN_ATENCION,"Reabierta por nueva revisión",1L));
        assertThat(saved.history()).hasSize(2);assertThat(saved.comment()).isEqualTo("Consulta de productos");
    }
    @Test void inboxIsPaginatedFilteredAndDoesNotExposeMessageContent(){
        var first=receive();contact.attend(admin,first,new Attention(Status.RESUELTA,"Atendida",0L));for(int i=0;i<21;i++)receive();
        var inbox=contact.inbox(admin,null,0);assertThat(inbox.items()).hasSize(20);assertThat(inbox.totalElements()).isEqualTo(22);assertThat(inbox.totalPages()).isEqualTo(2);
        assertThat(contact.inbox(admin,null,1).items()).hasSize(2);assertThat(contact.inbox(admin,Status.RESUELTA,0).items()).extracting(Summary::id).containsExactly(first);
        assertThat(contact.inbox(admin,Status.PENDIENTE,0).totalElements()).isEqualTo(21);
    }
    @Test void unknownDetailAndInvalidFiltersUseHttpErrors()throws Exception{
        var t=token(admin);http.perform(get("/api/v1/admin/contact/99999999").header("Authorization",t)).andExpect(status().isNotFound());
        http.perform(get("/api/v1/admin/contact?status=OTRO").header("Authorization",t)).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/admin/contact?page=-1").header("Authorization",t)).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/admin/contact?page=2147483647").header("Authorization",t)).andExpect(status().isBadRequest());
    }
    @Test void changingRoleRevokesAdministrativeAccessEvenWithPreviousToken()throws Exception{
        var t=token(admin);var account=users.findById(admin).orElseThrow();account.role=Role.CLIENTE;users.saveAndFlush(account);
        http.perform(get("/api/v1/admin/contact").header("Authorization",t)).andExpect(status().isForbidden());
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentRetriesCreateOneReceipt()throws Exception{
        var data=submission();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var tasks=List.of(pool.submit(()->{start.await();return contact.receive(data);}),pool.submit(()->{start.await();return contact.receive(data);}));start.countDown();
            assertThat(tasks.get(0).get(10,TimeUnit.SECONDS)).isEqualTo(tasks.get(1).get(10,TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("select count(*) from contact_message where submission_key=?",Long.class,data.submissionKey())).isEqualTo(1);
        }finally{pool.shutdownNow();jdbc.update("delete from contact_message where submission_key=?",data.submissionKey());jdbc.update("delete from app_user where id in (?,?,?)",admin,client,seller);}
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentAttentionKeepsOneWriterPerVersion()throws Exception{
        var id=receive();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var tasks=new ArrayList<Future<Boolean>>();for(var note:List.of("Atención A","Atención B"))tasks.add(pool.submit(()->{start.await();try{contact.attend(admin,id,new Attention(Status.EN_ATENCION,note,0L));return true;}catch(ApiException expected){return false;}}));
            start.countDown();int successes=0;for(var task:tasks)if(task.get(10,TimeUnit.SECONDS))successes++;assertThat(successes).isEqualTo(1);assertThat(contact.detail(admin,id).history()).hasSize(1);
        }finally{pool.shutdownNow();jdbc.update("delete from contact_event where message_id=?",id);jdbc.update("delete from contact_message where id=?",id);jdbc.update("delete from app_user where id in (?,?,?)",admin,client,seller);}
    }
}
