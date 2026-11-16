package cl.kynex.identity;

import cl.kynex.commerce.service.CartService;
import cl.kynex.commerce.service.OrderService;
import cl.kynex.identity.model.AppUser;
import cl.kynex.identity.model.Role;
import cl.kynex.identity.repository.UserRepository;
import cl.kynex.identity.service.AuthService;
import cl.kynex.identity.service.UserService;
import cl.kynex.support.TestDatabaseGuard;
import cl.kynex.identity.dto.AuthDtos;
import cl.kynex.identity.dto.UserDtos.*;
import cl.kynex.commerce.dto.CommerceDtos;
import cl.kynex.shared.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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

/** Identidad, bajas, permisos, versiones y concurrencia con PostgreSQL real. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@ContextConfiguration(initializers=TestDatabaseGuard.class) @Transactional
class UserIntegrationTest {
    @Autowired UserService service;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired CartService carts;
    @Autowired OrderService orders;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    static final String PASSWORD="ClaveUsuarios2026!";
    static final String HASH=new BCryptPasswordEncoder(4).encode(PASSWORD);
    static final AtomicInteger sequence=new AtomicInteger(41000000);
    Long admin,client;
    @BeforeEach void setup() { admin=user(Role.ADMINISTRADOR).id;client=user(Role.CLIENTE).id; }
    static String run() {
        var value=String.valueOf(sequence.incrementAndGet());int sum=0,m=2;
        for(int i=value.length()-1;i>=0;i--){sum+=(value.charAt(i)-'0')*m;m=m==7?2:m+1;}
        int digit=11-sum%11;return value+(digit==11?"0":digit==10?"K":String.valueOf(digit));
    }
    AppUser user(Role role) {
        var user=new AppUser();user.run=run();user.firstName="Daniela";user.lastName="Muñoz Soto";user.email=UUID.randomUUID()+"@example.com";
        user.region="Región Metropolitana de Santiago";user.commune="Santiago";user.address="Calle de prueba 123";user.role=role;user.passwordHash=HASH;
        return users.saveAndFlush(user);
    }
    ProfileData data(Long id) { var u=users.findById(id).orElseThrow();return new ProfileData(u.run,u.firstName,u.lastName,u.email,u.birthDate,u.region,u.commune,u.address); }
    AuthDtos.Register registration() { return new AuthDtos.Register(run(),"Elena","Prueba",UUID.randomUUID()+"@example.com",PASSWORD,null,"Región Metropolitana de Santiago","Santiago","Avenida 456"); }
    String token(Long id) { return "Bearer "+auth.login(new AuthDtos.Login(users.findById(id).orElseThrow().email,PASSWORD)).body().accessToken(); }
    String body(Object data)throws Exception { return json.writeValueAsString(data); }
    @Test void adminListsAndReadsWithoutExposingHashes()throws Exception {
        var token=token(admin);
        http.perform(get("/api/v1/admin/users").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].version").exists())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password"))));
        http.perform(get("/api/v1/admin/users/"+client).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("Cliente"));
    }
    @Test void administrativeCreationUsesChosenPasswordAndRole() {
        var input=registration();var created=service.create(admin,new Create(input,Role.VENDEDOR));
        var stored=users.findById(created.id()).orElseThrow();assertThat(stored.passwordHash).isNotEqualTo(input.password());
        assertThat(new BCryptPasswordEncoder().matches(input.password(),stored.passwordHash)).isTrue();
        assertThat(auth.login(new AuthDtos.Login(created.email(),PASSWORD)).body().user().role()).isEqualTo("Vendedor");
    }
    @Test void administratorCanCreateAnotherAdministrator() {
        assertThat(service.create(admin,new Create(registration(),Role.ADMINISTRADOR)).role()).isEqualTo("Administrador");
    }
    @Test void adminEditsProfileAndRoleButKeepsPassword() {
        var before=users.findById(client).orElseThrow().passwordHash;
        var saved=service.update(admin,client,new Update(0L,data(client),Role.VENDEDOR));
        assertThat(saved.role()).isEqualTo("Vendedor");assertThat(saved.version()).isEqualTo(1);
        assertThat(users.findById(client).orElseThrow().passwordHash).isEqualTo(before);
    }
    @Test void staleEditsDoNotOverwriteProfile() {
        var input=data(client);service.update(admin,client,new Update(0L,input,Role.VENDEDOR));
        assertThatThrownBy(()->service.updateProfile(client,new ProfileInput(0L,input))).isInstanceOf(ApiException.class);
        assertThat(service.profile(client).role()).isEqualTo("Vendedor");
    }
    @Test void deactivationPreservesCartAndHistoricalOrders() {
        var cart=carts.quantity(client,2L,new CommerceDtos.CartQuantity(2,0L));
        var order=orders.checkout(client,new CommerceDtos.Checkout(UUID.randomUUID(),cart.version(),new CommerceDtos.Delivery("Daniela Muñoz","Región Metropolitana de Santiago","Santiago","Calle 123")));
        service.deactivate(admin,client,0L);assertThat(users.findById(client).orElseThrow().active).isFalse();
        assertThat(orders.managed(order.id()).products()).hasSize(1);
        service.activate(admin,client,1L);assertThat(carts.get(client).totalItems()).isEqualTo(2);
    }
    @Test void deactivationRevokesAccessAndRefreshEvenAfterReactivation()throws Exception {
        var login=auth.login(new AuthDtos.Login(users.findById(client).orElseThrow().email,PASSWORD));
        service.deactivate(admin,client,0L);service.activate(admin,client,1L);
        http.perform(get("/api/v1/profile").header("Authorization","Bearer "+login.body().accessToken())).andExpect(status().isUnauthorized());
        assertThatThrownBy(()->auth.refresh(login.refreshToken())).isInstanceOf(ApiException.class);
        assertThat(auth.login(new AuthDtos.Login(users.findById(client).orElseThrow().email,PASSWORD)).body().user().id()).isEqualTo(client);
    }
    @Test void inactiveAccountCannotLogin() {
        service.deactivate(admin,client,0L);
        assertThatThrownBy(()->auth.login(new AuthDtos.Login(users.findById(client).orElseThrow().email,PASSWORD))).isInstanceOf(ApiException.class);
    }
    @Test void administratorCannotDisableOwnAccount() {
        assertThatThrownBy(()->service.deactivate(admin,admin,0L)).isInstanceOf(ApiException.class);
        assertThat(users.findById(admin).orElseThrow().active).isTrue();
    }
    @Test void administratorCannotDemoteOwnAccountEvenWithAnotherAdministrator() {
        user(Role.ADMINISTRADOR);
        assertThatThrownBy(()->service.update(admin,admin,new Update(0L,data(admin),Role.CLIENTE))).isInstanceOf(ApiException.class);
    }
    @Test void roleChangeImmediatelyRestrictsAlreadyIssuedJwt()throws Exception {
        var other=user(Role.ADMINISTRADOR);var token=token(other.id);
        service.update(admin,other.id,new Update(0L,data(other.id),Role.VENDEDOR));
        http.perform(get("/api/v1/admin/users").header("Authorization",token)).andExpect(status().isForbidden());
        http.perform(get("/api/v1/admin/orders").header("Authorization",token)).andExpect(status().isOk());
    }
    @Test void staleActivationAndDeactivationAreRejected() {
        service.update(admin,client,new Update(0L,data(client),Role.VENDEDOR));
        assertThatThrownBy(()->service.deactivate(admin,client,0L)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.activate(admin,client,0L)).isInstanceOf(ApiException.class);
    }
    @Test void personalProfileIsAlwaysTheAuthenticatedAccount()throws Exception {
        http.perform(get("/api/v1/profile?userId="+admin).header("Authorization",token(client))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(client));
    }
    @Test void ownProfileUpdatesAddressAndPreservesRoleAndPassword() {
        var u=data(client);var changed=new ProfileData(u.run(),"Nombre nuevo",u.lastName(),u.email(),u.birthDate(),u.region(),u.commune(),"Nueva dirección 456");
        var saved=service.updateProfile(client,new ProfileInput(0L,changed));assertThat(saved.firstName()).isEqualTo("Nombre nuevo");assertThat(saved.role()).isEqualTo("Cliente");
        assertThat(users.findById(client).orElseThrow().passwordHash).isEqualTo(HASH);assertThat(saved.version()).isEqualTo(1);
    }
    @Test void profileDoesNotModifyPurchaseSnapshot() {
        var cart=carts.quantity(client,2L,new CommerceDtos.CartQuantity(1,0L));var before=data(client);
        var order=orders.checkout(client,new CommerceDtos.Checkout(UUID.randomUUID(),cart.version(),new CommerceDtos.Delivery("Original",before.region(),before.commune(),before.address())));
        service.updateProfile(client,new ProfileInput(0L,new ProfileData(before.run(),"Actual",before.lastName(),before.email(),null,before.region(),before.commune(),"Nueva dirección")));
        assertThat(orders.get(client,order.id()).delivery().address()).isEqualTo(before.address());
        assertThat(orders.get(client,order.id()).customer().get("name")).startsWith("Daniela");
    }
    @Test void duplicateEmailAndRunAreRejected() {
        var current=data(client);var other=data(admin);
        assertThatThrownBy(()->service.updateProfile(client,new ProfileInput(0L,new ProfileData(current.run(),current.firstName(),current.lastName(),other.email(),null,current.region(),current.commune(),current.address())))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.updateProfile(client,new ProfileInput(0L,new ProfileData(other.run(),current.firstName(),current.lastName(),current.email(),null,current.region(),current.commune(),current.address())))).isInstanceOf(ApiException.class);
    }
    @Test void personalEndpointRejectsPrivilegeAndOwnerInjection()throws Exception {
        var payload=new LinkedHashMap<String,Object>();payload.put("version",0);payload.put("profile",json.convertValue(data(client),Map.class));payload.put("role","ADMINISTRADOR");
        http.perform(put("/api/v1/profile").header("Authorization",token(client)).contentType(MediaType.APPLICATION_JSON).content(body(payload))).andExpect(status().isBadRequest());
        payload.remove("role");((Map<String,Object>)payload.get("profile")).put("id",admin);
        http.perform(put("/api/v1/profile").header("Authorization",token(client)).contentType(MediaType.APPLICATION_JSON).content(body(payload))).andExpect(status().isBadRequest());
    }
    @Test void clientAndSellerCannotManageUsersEvenThroughDirectRequests()throws Exception {
        for(var actor:List.of(client,user(Role.VENDEDOR).id)) {
            var token=token(actor);
            http.perform(get("/api/v1/admin/users").header("Authorization",token)).andExpect(status().isForbidden());
            http.perform(get("/api/v1/admin/users/"+admin).header("Authorization",token)).andExpect(status().isForbidden());
            http.perform(post("/api/v1/admin/users").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body(new Create(registration(),Role.ADMINISTRADOR)))).andExpect(status().isForbidden());
            http.perform(put("/api/v1/admin/users/"+admin).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body(new Update(0L,data(admin),Role.CLIENTE)))).andExpect(status().isForbidden());
            http.perform(delete("/api/v1/admin/users/"+admin+"?version=0").header("Authorization",token)).andExpect(status().isForbidden());
            http.perform(put("/api/v1/admin/users/"+admin+"/activation").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isForbidden());
        }
    }
    @Test void anonymousRequestsCannotReadProfilesOrUsers()throws Exception {
        http.perform(get("/api/v1/profile")).andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    }
    @Test void invalidProfileFieldsProduceFieldErrors()throws Exception {
        var u=data(client);var profile=new ProfileData(u.run(),u.firstName(),u.lastName(),u.email(),LocalDate.now().plusDays(1),u.region(),u.commune(),u.address());
        http.perform(put("/api/v1/profile").header("Authorization",token(client)).contentType(MediaType.APPLICATION_JSON).content(body(new ProfileInput(0L,profile))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors['profile.birthDate']").exists());
    }
    @Test void administrativeHttpCreateUpdateAndDeactivateReturnExpectedStatus()throws Exception {
        var token=token(admin);
        var created=http.perform(post("/api/v1/admin/users").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body(new Create(registration(),Role.CLIENTE))))
            .andExpect(status().isCreated()).andReturn().getResponse();var record=json.readTree(created.getContentAsString());long id=record.get("id").asLong(),version=record.get("version").asLong();
        http.perform(put("/api/v1/admin/users/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body(new Update(version,data(id),Role.VENDEDOR))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("Vendedor"));
        http.perform(delete("/api/v1/admin/users/"+id+"?version="+(version+1)).header("Authorization",token)).andExpect(status().isNoContent());
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void deactivationAndRefreshDoNotDeadlockOrLeaveAnActiveSession()throws Exception {
        var before=auth.login(new AuthDtos.Login(users.findById(client).orElseThrow().email,PASSWORD));
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var renewal=pool.submit(()->{start.await();try{return auth.refresh(before.refreshToken());}catch(ApiException denied){return null;}});
            var disabling=pool.submit(()->{start.await();service.deactivate(admin,client,0L);return true;});
            start.countDown();var refreshed=renewal.get(10,TimeUnit.SECONDS);assertThat(disabling.get(10,TimeUnit.SECONDS)).isTrue();
            assertThat(users.findById(client).orElseThrow().active).isFalse();
            assertThat(jdbc.queryForObject("select count(*) from refresh_session where user_id=? and revoked=false",Long.class,client)).isZero();
            service.activate(admin,client,1L);
            if(refreshed!=null)http.perform(get("/api/v1/profile").header("Authorization","Bearer "+refreshed.body().accessToken())).andExpect(status().isUnauthorized());
        } finally {
            pool.shutdownNow();jdbc.update("delete from refresh_session where user_id in (?,?)",admin,client);
            jdbc.update("delete from app_user where id in (?,?)",admin,client);
        }
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentAdministratorsCannotDemoteEachOtherAndRemoveAllAdministration()throws Exception {
        var first=user(Role.ADMINISTRADOR);var second=user(Role.ADMINISTRADOR);var firstData=data(first.id);var secondData=data(second.id);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var futures=List.of(pool.submit(()->{start.await();try{service.update(first.id,second.id,new Update(0L,secondData,Role.CLIENTE));return true;}catch(ApiException expected){return false;}}),
                pool.submit(()->{start.await();try{service.update(second.id,first.id,new Update(0L,firstData,Role.CLIENTE));return true;}catch(ApiException expected){return false;}}));
            start.countDown();int success=0;for(var future:futures)if(future.get(10,TimeUnit.SECONDS))success++;
            assertThat(success).isEqualTo(1);assertThat(users.findAllById(List.of(first.id,second.id)).stream().filter(u->u.role==Role.ADMINISTRADOR&&u.active).count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();for(var id:List.of(first.id,second.id,admin,client))jdbc.update("delete from app_user where id=?",id);
        }
    }
}
