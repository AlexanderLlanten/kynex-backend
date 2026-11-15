package cl.kynex;

import cl.kynex.dto.AuthDtos;
import cl.kynex.dto.ContactDtos.*;
import cl.kynex.entity.*;
import cl.kynex.entity.ContactMessage.Status;
import cl.kynex.repository.*;
import cl.kynex.service.*;
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
class ContactRepliesIntegrationTest {
    @Autowired ContactService contact;
    @Autowired ContactMessageRepository messages;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    static final String PASSWORD="ClaveRespuestas2026!",HASH=new BCryptPasswordEncoder(4).encode(PASSWORD);
    Long admin,client,seller;
    @BeforeEach void setup(){admin=user(Role.ADMINISTRADOR);client=user(Role.CLIENTE);seller=user(Role.VENDEDOR);}
    Long user(Role role){var u=new AppUser();u.firstName="Prueba";u.lastName="Respuestas";u.email=UUID.randomUUID()+"@gmail.com";u.role=role;u.passwordHash=HASH;return users.saveAndFlush(u).id;}
    String token(Long id){return "Bearer "+auth.login(new AuthDtos.Login(users.findById(id).orElseThrow().email,PASSWORD)).body().accessToken();}
    String body(Object data)throws Exception{return json.writeValueAsString(data);}
    Long owned(Long owner){var key=UUID.randomUUID();contact.receiveOwned(owner,new AccountSubmission(key,"Consulta personal"));return messages.findBySubmissionKey(key).orElseThrow().id;}
    UUID legacy(Long owner){var key=UUID.randomUUID();contact.receive(new Submission(key,"Nombre anterior",users.findById(owner).orElseThrow().email,"Consulta anterior"));return key;}
    @Test void signedSubmissionDerivesOwnerAndIdentityFromAccount()throws Exception{
        var key=UUID.randomUUID();http.perform(post("/api/v1/my/contact").header("Authorization",token(client)).contentType(MediaType.APPLICATION_JSON).content(body(new AccountSubmission(key,"  Consulta personal  "))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.reference").value(key.toString()));
        var m=messages.findBySubmissionKey(key).orElseThrow();assertThat(m.ownerId).isEqualTo(client);assertThat(m.name).isEqualTo("Prueba Respuestas");assertThat(m.email).isEqualTo(users.findById(client).orElseThrow().email);assertThat(m.comment).isEqualTo("Consulta personal");
    }
    @Test void signedSubmissionRejectsSpoofedOwnerEmailAndName()throws Exception{
        for(var field:List.of("\"ownerId\":"+seller,"\"email\":\"otra@gmail.com\"","\"name\":\"Otra persona\""))
            http.perform(post("/api/v1/my/contact").header("Authorization",token(client)).contentType(MediaType.APPLICATION_JSON).content("{\"submissionKey\":\""+UUID.randomUUID()+"\",\"comment\":\"Consulta\","+field+"}")).andExpect(status().isBadRequest());
    }
    @Test void ownedRetriesDoNotCreateDuplicatesOrBecomePublic(){
        var data=new AccountSubmission(UUID.randomUUID(),"Consulta personal");contact.receiveOwned(client,data);contact.receiveOwned(client,data);
        assertThat(contact.mine(client,0).items()).hasSize(1);assertThat(contact.mine(seller,0).items()).isEmpty();
        assertThatThrownBy(()->contact.receive(new Submission(data.submissionKey(),"Prueba Respuestas",users.findById(client).orElseThrow().email,"Consulta personal"))).isInstanceOf(cl.kynex.exception.ApiException.class);
    }
    @Test void accountWithGeneralEmailCanUseItsOwnContactIdentity(){
        var account=users.findById(client).orElseThrow();account.email=UUID.randomUUID()+"@example.org";users.saveAndFlush(account);
        assertThat(contact.mine(client,owned(client)).email()).endsWith("@example.org");
    }
    @Test void ownDetailDoesNotExposePrivateNotesInternalHistoryOrStaffIdentity()throws Exception{
        var id=owned(client);contact.attend(admin,id,new Attention(Status.RESUELTA,"NOTA PRIVADA",0L));contact.reply(admin,id,new ReplyInput(UUID.randomUUID(),"Respuesta visible",1L));
        var result=http.perform(get("/api/v1/my/contact/"+id).header("Authorization",token(client))).andExpect(status().isOk())
            .andExpect(jsonPath("$.replies[0].text").value("Respuesta visible")).andExpect(jsonPath("$.note").doesNotExist()).andExpect(jsonPath("$.history").doesNotExist())
            .andExpect(jsonPath("$.replies[0].actorName").doesNotExist()).andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain("NOTA PRIVADA");
    }
    @Test void publishedRepliesArePersistentOrderedAndSeparateFromNotes(){
        var id=owned(client);var first=contact.reply(admin,id,new ReplyInput(UUID.randomUUID(),"Primera respuesta",0L));
        var second=contact.reply(admin,id,new ReplyInput(UUID.randomUUID(),"Segunda respuesta",first.version()));
        assertThat(contact.mine(client,id).replies()).extracting(ReplyView::text).containsExactly("Primera respuesta","Segunda respuesta");
        assertThat(second.note()).isEmpty();assertThat(second.status()).isEqualTo(Status.EN_ATENCION);
    }
    @Test void previousNotesStayPrivateAfterLinkingLegacyRequest(){
        var key=legacy(client);var id=messages.findBySubmissionKey(key).orElseThrow().id;contact.attend(admin,id,new Attention(Status.RESUELTA,"Atención interna anterior",0L));
        assertThat(contact.mine(client,0).items()).isEmpty();var claimed=contact.claim(client,new Claim(key));assertThat(claimed.replies()).isEmpty();
        assertThat(contact.detail(admin,id).note()).isEqualTo("Atención interna anterior");assertThat(contact.mine(client,0).items()).hasSize(1);
    }
    @Test void legacyLinkRequiresReferenceAndMatchingAccountEmail()throws Exception{
        var key=legacy(client);var t=token(seller);
        http.perform(post("/api/v1/my/contact/claims").header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content(body(new Claim(key)))).andExpect(status().isNotFound());
        http.perform(post("/api/v1/my/contact/claims").header("Authorization",token(client)).contentType(MediaType.APPLICATION_JSON).content(body(new Claim(UUID.randomUUID())))).andExpect(status().isNotFound());
        assertThat(contact.mine(client,0).items()).isEmpty();
    }
    @Test void linkingIsIdempotentAndCannotTransferOwnership(){
        var key=legacy(client);var first=contact.claim(client,new Claim(key));assertThat(contact.claim(client,new Claim(key))).isEqualTo(first);
        assertThatThrownBy(()->contact.claim(seller,new Claim(key))).isInstanceOf(cl.kynex.exception.ApiException.class);assertThat(contact.mine(client,0).items()).hasSize(1);
    }
    @Test void ownerKeepsAccessAfterChangingEmail(){
        var id=owned(client);var account=users.findById(client).orElseThrow();account.email=UUID.randomUUID()+"@gmail.com";users.saveAndFlush(account);
        assertThat(contact.mine(client,id).id()).isEqualTo(id);
    }
    @Test void anyOtherAccountIncludingAdministratorCannotReadOwnEndpointForSomeoneElse()throws Exception{
        var id=owned(client);for(var actor:List.of(seller,admin))http.perform(get("/api/v1/my/contact/"+id).header("Authorization",token(actor))).andExpect(status().isNotFound());
    }
    @Test void anonymousCannotUseAccountInboxSubmissionClaimOrReplies()throws Exception{
        var id=owned(client);http.perform(get("/api/v1/my/contact")).andExpect(status().isUnauthorized());http.perform(get("/api/v1/my/contact/"+id)).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/my/contact").contentType(MediaType.APPLICATION_JSON).content(body(new AccountSubmission(UUID.randomUUID(),"Consulta")))).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/my/contact/claims").contentType(MediaType.APPLICATION_JSON).content(body(new Claim(UUID.randomUUID())))).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/admin/contact/"+id+"/replies").contentType(MediaType.APPLICATION_JSON).content(body(new ReplyInput(UUID.randomUUID(),"Respuesta",0L)))).andExpect(status().isUnauthorized());
    }
    @Test void clientsAndSellersCannotPublishResponses()throws Exception{
        var id=owned(client);for(var actor:List.of(client,seller))http.perform(post("/api/v1/admin/contact/"+id+"/replies").header("Authorization",token(actor)).contentType(MediaType.APPLICATION_JSON).content(body(new ReplyInput(UUID.randomUUID(),"Respuesta falsa",0L)))).andExpect(status().isForbidden());
    }
    @Test void replyRetryUsesSameKeyWithoutDuplicatingOrOverwriting(){
        var id=owned(client);var data=new ReplyInput(UUID.randomUUID(),"Respuesta enviada",0L);var first=contact.reply(admin,id,data);var retry=contact.reply(admin,id,data);
        assertThat(retry.version()).isEqualTo(first.version());assertThat(retry.replies()).hasSize(1);
        assertThatThrownBy(()->contact.reply(admin,id,new ReplyInput(data.replyKey(),"Cambio no permitido",first.version()))).isInstanceOf(cl.kynex.exception.ApiException.class);
    }
    @Test void staleReplyDoesNotPublishOrReplaceLatestAttention()throws Exception{
        var id=owned(client);contact.attend(admin,id,new Attention(Status.EN_ATENCION,"Atención vigente",0L));
        http.perform(post("/api/v1/admin/contact/"+id+"/replies").header("Authorization",token(admin)).contentType(MediaType.APPLICATION_JSON).content(body(new ReplyInput(UUID.randomUUID(),"Desactualizada",0L)))).andExpect(status().isConflict());
        assertThat(contact.mine(client,id).replies()).isEmpty();assertThat(contact.detail(admin,id).note()).isEqualTo("Atención vigente");
    }
    @Test void replyRejectsBlankOversizedMissingKeyAndUnknownFields()throws Exception{
        var id=owned(client);var t=token(admin);
        for(var data:List.of(new ReplyInput(UUID.randomUUID()," ",0L),new ReplyInput(UUID.randomUUID(),"a".repeat(2001),0L),new ReplyInput(null,"Respuesta",0L)))
            http.perform(post("/api/v1/admin/contact/"+id+"/replies").header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content(body(data))).andExpect(status().isBadRequest());
        http.perform(post("/api/v1/my/contact/claims").header("Authorization",t).contentType(MediaType.APPLICATION_JSON).content("{\"reference\":\"incorrecta\"}")).andExpect(status().isBadRequest());
    }
    @Test void ownInboxIsPaginatedAndContainsOnlyOwnedRequests(){
        for(int i=0;i<21;i++)owned(client);owned(seller);legacy(client);
        var result=contact.mine(client,0);assertThat(result.items()).hasSize(20);assertThat(result.totalElements()).isEqualTo(21);assertThat(contact.mine(client,1).items()).hasSize(1);
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentResponseRetriesPublishExactlyOnce()throws Exception{
        var id=owned(client);var data=new ReplyInput(UUID.randomUUID(),"Respuesta única",0L);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var tasks=List.of(pool.submit(()->{start.await();return contact.reply(admin,id,data);}),pool.submit(()->{start.await();return contact.reply(admin,id,data);}));start.countDown();
            assertThat(tasks.get(0).get(10,TimeUnit.SECONDS).replies()).hasSize(1);assertThat(tasks.get(1).get(10,TimeUnit.SECONDS).replies()).hasSize(1);assertThat(contact.mine(client,id).replies()).hasSize(1);
        }finally{pool.shutdownNow();jdbc.update("delete from contact_reply where message_id=?",id);jdbc.update("delete from contact_event where message_id=?",id);jdbc.update("delete from contact_message where id=?",id);jdbc.update("delete from app_user where id in (?,?,?)",admin,client,seller);}
    }
}
