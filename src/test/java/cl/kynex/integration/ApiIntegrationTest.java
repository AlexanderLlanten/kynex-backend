package cl.kynex.integration;

import cl.kynex.catalog.dto.CatalogDtos;
import cl.kynex.catalog.model.Category;
import cl.kynex.catalog.repository.CategoryRepository;
import cl.kynex.catalog.repository.ProductRepository;
import cl.kynex.catalog.service.CatalogService;
import cl.kynex.identity.model.AppUser;
import cl.kynex.identity.model.Role;
import cl.kynex.identity.repository.RefreshSessionRepository;
import cl.kynex.identity.repository.UserRepository;
import cl.kynex.shared.exception.ApiException;
import cl.kynex.support.TestDatabaseGuard;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Prueba filtros reales, controladores, servicios, JPA y PostgreSQL; no utiliza H2. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.context.ContextConfiguration(initializers=TestDatabaseGuard.class)
@Transactional
class ApiIntegrationTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired RefreshSessionRepository sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired cl.kynex.catalog.service.CatalogService catalog;
    private static final String PASSWORD = "ClavePruebas2026!";
    private static final String HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);

    @BeforeEach void isolatedDatabase() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).endsWith("_test");
    }
    record LoginResult(String access, Cookie refresh) {}
    private RequestPostProcessor browserCsrf() throws Exception {
        var response = http.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        String token = json.readTree(response.getContentAsString()).get("token").asText();
        Cookie cookie = response.getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return request -> {
            var cookies = new ArrayList<Cookie>();
            if (request.getCookies() != null) cookies.addAll(List.of(request.getCookies()));
            cookies.add(cookie); request.setCookies(cookies.toArray(Cookie[]::new));
            request.addHeader("X-XSRF-TOKEN", token); return request;
        };
    }
    private AppUser account(Role role) {
        var user = new AppUser(); user.firstName = "Prueba"; user.lastName = "KYNEX";
        user.email = UUID.randomUUID() + "@example.com"; user.passwordHash = HASH; user.role = role;
        return users.saveAndFlush(user);
    }
    private LoginResult login(AppUser user) throws Exception {
        var response = http.perform(post("/api/v1/auth/login").with(browserCsrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", user.email, "password", PASSWORD))))
            .andExpect(status().isOk()).andReturn().getResponse();
        return new LoginResult(json.readTree(response.getContentAsString()).get("accessToken").asText(), response.getCookie("kynex_refresh"));
    }
    private String body(Object value) throws Exception { return json.writeValueAsString(value); }
    private Map<String,Object> productBody() {
        return new LinkedHashMap<>(Map.of("code", "TEST001", "name", "Producto de prueba", "categoryId", 1,
            "description", "Descripción", "price", 19990, "stock", 5, "featured", false,
            "details", Map.of("format", "300 g")));
    }

    @Test void publicCatalogHasSixProductsAndMetadata() throws Exception {
        http.perform(get("/api/v1/products")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6))
            .andExpect(jsonPath("$[1].code").value("KYN002")).andExpect(jsonPath("$[1].usage.serving").value("5 g"))
            .andExpect(jsonPath("$[1].categoryId").value(2));
    }
    @Test void publicImagesAreActualPngs() throws Exception {
        http.perform(get("/media/products/kynex-creatina-monohidratada-unflavored.png"))
            .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
    }
    @Test void swaggerDocumentsVersionedPaths() throws Exception {
        http.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/products']").exists()).andExpect(jsonPath("$.paths['/api/v1/auth/refresh']").exists());
    }
    @Test void unknownProductReturns404() throws Exception { http.perform(get("/api/v1/products/999999")).andExpect(status().isNotFound()); }
    @Test void existingProductDetailIncludesItsCategoryReference() throws Exception {
        http.perform(get("/api/v1/products/1")).andExpect(status().isOk())
            .andExpect(jsonPath("$.category").value("Proteínas")).andExpect(jsonPath("$.categoryId").value(1));
    }
    @Test void anonymousCannotWriteProducts() throws Exception { http.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body(productBody()))).andExpect(status().isUnauthorized()); }
    @Test void clientCannotWriteProducts() throws Exception {
        var session = login(account(Role.CLIENTE));
        http.perform(post("/api/v1/products").header("Authorization", "Bearer " + session.access()).contentType(MediaType.APPLICATION_JSON).content(body(productBody()))).andExpect(status().isForbidden());
    }
    @Test void sellerCanReadButCannotWriteProducts() throws Exception {
        var session = login(account(Role.VENDEDOR));
        http.perform(get("/api/v1/products").header("Authorization", "Bearer " + session.access())).andExpect(status().isOk());
        http.perform(post("/api/v1/products").header("Authorization", "Bearer " + session.access()).contentType(MediaType.APPLICATION_JSON).content(body(productBody()))).andExpect(status().isForbidden());
    }
    @Test void adminCreatesUpdatesAndSoftDeletesProduct() throws Exception {
        var session = login(account(Role.ADMINISTRADOR)); String auth = "Bearer " + session.access();
        var data = productBody();
        var created = http.perform(post("/api/v1/products").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body(data)))
            .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        data.put("version", 0); data.put("stock", 3);
        http.perform(put("/api/v1/products/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body(data)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.stock").value(3)).andExpect(jsonPath("$.version").value(1));
        http.perform(delete("/api/v1/products/" + id + "?version=1").header("Authorization", auth)).andExpect(status().isNoContent());
        http.perform(get("/api/v1/products/" + id)).andExpect(status().isNotFound());
        assertThat(products.findById(id).orElseThrow().active).isFalse();
    }
    @Test void staleUpdateDoesNotOverwriteInventory() throws Exception {
        String auth = "Bearer " + login(account(Role.ADMINISTRADOR)).access();
        var data = productBody(); data.put("version", 999);
        http.perform(put("/api/v1/products/1").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body(data))).andExpect(status().isConflict());
        assertThat(products.findById(1L).orElseThrow().stock).isEqualTo(18);
    }
    @Test void negativeStockIsRejected() throws Exception {
        String auth = "Bearer " + login(account(Role.ADMINISTRADOR)).access(); var data = productBody(); data.put("stock", -1);
        http.perform(post("/api/v1/products").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body(data)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.stock").exists());
    }
    @Test void duplicateCodeIsRejectedByDatabase() throws Exception {
        String auth = "Bearer " + login(account(Role.ADMINISTRADOR)).access(); var data = productBody(); data.put("code", "KYN001");
        http.perform(post("/api/v1/products").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body(data))).andExpect(status().isConflict());
    }
    @Test void categoryWithProductsCannotBeDeleted() throws Exception {
        String auth = "Bearer " + login(account(Role.ADMINISTRADOR)).access();
        http.perform(delete("/api/v1/categories/1?version=0").header("Authorization", auth)).andExpect(status().isConflict());
    }
    @Test void categoryCrudPersistsAndHidesInactiveCategory() throws Exception {
        String auth = "Bearer " + login(account(Role.ADMINISTRADOR)).access();
        var created = http.perform(post("/api/v1/categories").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pruebas\"}"))
            .andExpect(status().isCreated()).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        http.perform(put("/api/v1/categories/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Actualizada\",\"version\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        http.perform(delete("/api/v1/categories/" + id + "?version=1").header("Authorization", auth)).andExpect(status().isNoContent());
        assertThat(categories.findById(id).orElseThrow().active).isFalse();
        http.perform(get("/api/v1/categories/"+id)).andExpect(status().isNotFound());
    }
    @Test void categoryDetailIsPublicAndIncludesVersion() throws Exception {
        http.perform(get("/api/v1/categories/2")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Creatinas")).andExpect(jsonPath("$.version").value(0));
    }
    @Test void categoryNameIsUniqueIgnoringCase() throws Exception {
        String token="Bearer "+login(account(Role.ADMINISTRADOR)).access();
        http.perform(post("/api/v1/categories").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  cReAtInAs  \"}"))
            .andExpect(status().isConflict());
    }
    @Test void categoryUniquenessIncludesAccentedUppercaseLetters() throws Exception {
        String token="Bearer "+login(account(Role.ADMINISTRADOR)).access();
        http.perform(post("/api/v1/categories").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("name","PROTEÍNAS"))))
            .andExpect(status().isConflict());
    }
    @Test void categoryUniquenessIncludesEquivalentUnicodeSpellings() throws Exception {
        String token="Bearer "+login(account(Role.ADMINISTRADOR)).access();
        http.perform(post("/api/v1/categories").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("name","Protei\u0301nas"))))
            .andExpect(status().isConflict());
    }
    @Test void staleCategoryEditsAndDeletesAreRejected() throws Exception {
        String token="Bearer "+login(account(Role.ADMINISTRADOR)).access();
        http.perform(put("/api/v1/categories/2").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Otra\",\"version\":9}"))
            .andExpect(status().isConflict());
        http.perform(delete("/api/v1/categories/2?version=9").header("Authorization",token)).andExpect(status().isConflict());
        assertThat(categories.findById(2L).orElseThrow().name).isEqualTo("Creatinas");
    }
    @Test void categoryNamesRequireContentAndRespectLength() throws Exception {
        String token="Bearer "+login(account(Role.ADMINISTRADOR)).access();
        for(var name:List.of("   ","a".repeat(81)))http.perform(post("/api/v1/categories").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("name",name))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());
    }
    @Test void categoryRenameAppearsInPublicProducts() throws Exception {
        String token="Bearer "+login(account(Role.ADMINISTRADOR)).access();
        http.perform(put("/api/v1/categories/2").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Creatinas actualizadas\",\"version\":0}"))
            .andExpect(status().isOk());
        http.perform(get("/api/v1/products/2")).andExpect(status().isOk()).andExpect(jsonPath("$.category").value("Creatinas actualizadas"));
    }
    @Test void clientAndSellerCannotMutateCategories() throws Exception {
        for(var role:List.of(Role.CLIENTE,Role.VENDEDOR)) {
            String token="Bearer "+login(account(role)).access();
            http.perform(post("/api/v1/categories").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nueva\"}")).andExpect(status().isForbidden());
            http.perform(put("/api/v1/categories/2").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nueva\",\"version\":0}")).andExpect(status().isForbidden());
            http.perform(delete("/api/v1/categories/2?version=0").header("Authorization",token)).andExpect(status().isForbidden());
        }
    }
    @Test void inactiveCategoryCannotReceiveProducts() throws Exception {
        var category=new cl.kynex.catalog.model.Category();category.name="Inactiva "+UUID.randomUUID();category.active=false;categories.saveAndFlush(category);
        String token="Bearer "+login(account(Role.ADMINISTRADOR)).access();var data=productBody();data.put("categoryId",category.id);
        http.perform(post("/api/v1/products").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body(data))).andExpect(status().isNotFound());
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void categoryDeactivationAndProductCreationPreserveActiveReferences() throws Exception {
        var category=catalog.createCategory(new cl.kynex.catalog.dto.CatalogDtos.CategoryInput("Concurrente "+UUID.randomUUID(),null));
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
        try {
            var creating=pool.submit(()->{start.await();try{
                catalog.createProduct(new cl.kynex.catalog.dto.CatalogDtos.ProductInput("RACE"+UUID.randomUUID().toString().substring(0,8),category.id(),"Prueba concurrente","",new BigDecimal("1000"),1,0,"",false,Map.of(),null));return true;
            }catch(cl.kynex.shared.exception.ApiException denied){return false;}});
            var disabling=pool.submit(()->{start.await();try{catalog.deleteCategory(category.id(),category.version());return true;}catch(cl.kynex.shared.exception.ApiException denied){return false;}});
            start.countDown();boolean created=creating.get(10,java.util.concurrent.TimeUnit.SECONDS),disabled=disabling.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(created).isNotEqualTo(disabled);
            var current=categories.findById(category.id()).orElseThrow();assertThat(current.active).isEqualTo(created);
            assertThat(products.existsByCategoryIdAndActiveTrue(category.id())).isEqualTo(created);
        } finally {
            pool.shutdownNow();jdbc.update("delete from product where category_id=?",category.id());jdbc.update("delete from category where id=?",category.id());
        }
    }
    private Map<String,Object> registration() { return new LinkedHashMap<>(Map.of("run", "12.345.678-5", "firstName", "Camila", "lastName", "Soto", "email", "nuevo@example.com", "password", PASSWORD,
        "region", "Región Metropolitana de Santiago", "commune", "Santiago", "address", "Dirección de prueba 123")); }
    @Test void registrationAlwaysCreatesClientWithHash() throws Exception {
        http.perform(post("/api/v1/auth/register").with(browserCsrf()).contentType(MediaType.APPLICATION_JSON).content(body(registration())))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("Cliente")).andExpect(jsonPath("$.passwordHash").doesNotExist());
        var user = users.findByEmail("nuevo@example.com").orElseThrow();
        assertThat(user.run).isEqualTo("123456785"); assertThat(user.passwordHash).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(user.region).isEqualTo("Región Metropolitana de Santiago");
        assertThat(user.commune).isEqualTo("Santiago"); assertThat(user.address).isEqualTo("Dirección de prueba 123");
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, user.passwordHash)).isTrue();
    }
    @Test void registrationRejectsRoleInjection() throws Exception {
        var data = registration(); data.put("role", "ADMINISTRADOR");
        http.perform(post("/api/v1/auth/register").with(browserCsrf()).contentType(MediaType.APPLICATION_JSON).content(body(data))).andExpect(status().isBadRequest());
        assertThat(users.findByEmail("nuevo@example.com")).isEmpty();
    }
    @Test void invalidRunIsRejected() throws Exception {
        var data = registration(); data.put("run", "12345678-9");
        http.perform(post("/api/v1/auth/register").with(browserCsrf()).contentType(MediaType.APPLICATION_JSON).content(body(data))).andExpect(status().isBadRequest());
    }
    @Test void unrelatedRegionAndCommuneAreRejected() throws Exception {
        var data = registration(); data.put("commune", "Temuco");
        http.perform(post("/api/v1/auth/register").with(browserCsrf()).contentType(MediaType.APPLICATION_JSON).content(body(data))).andExpect(status().isBadRequest());
        assertThat(users.findByEmail("nuevo@example.com")).isEmpty();
    }
    @Test void missingAddressAndFutureBirthDateAreRejected() throws Exception {
        var data = registration(); data.put("address", ""); data.put("birthDate", "2999-01-01");
        http.perform(post("/api/v1/auth/register").with(browserCsrf()).contentType(MediaType.APPLICATION_JSON).content(body(data)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.address").exists()).andExpect(jsonPath("$.errors.birthDate").exists());
    }
    @Test void loginNeedsCsrfToken() throws Exception { http.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden()); }
    @Test void realCsrfCookieAndHeaderPermitLogin() throws Exception {
        var user = account(Role.CLIENTE);
        var response = http.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        String token = json.readTree(response.getContentAsString()).get("token").asText();
        http.perform(post("/api/v1/auth/login").cookie(response.getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", token)
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", user.email, "password", PASSWORD))))
            .andExpect(status().isOk()).andExpect(cookie().httpOnly("kynex_refresh", true));
    }
    @Test void invalidCredentialsDoNotRevealAccountExistence() throws Exception {
        http.perform(post("/api/v1/auth/login").with(browserCsrf()).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", "missing@example.com", "password", PASSWORD))))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("La sesión o las credenciales no son válidas."));
    }
    @Test void renewalRotatesAndReuseRevokesFamily() throws Exception {
        var session = login(account(Role.CLIENTE));
        var renewed = http.perform(post("/api/v1/auth/refresh").with(browserCsrf()).cookie(session.refresh())).andExpect(status().isOk()).andReturn().getResponse();
        Cookie next = renewed.getCookie("kynex_refresh"); assertThat(next.getValue()).isNotEqualTo(session.refresh().getValue());
        assertThat(sessions.findAll()).noneMatch(value -> value.tokenHash.equals(next.getValue()));
        http.perform(post("/api/v1/auth/refresh").with(browserCsrf()).cookie(session.refresh())).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/auth/refresh").with(browserCsrf()).cookie(next)).andExpect(status().isUnauthorized());
    }
    @Test void logoutRevokesRefreshAndAccessToken() throws Exception {
        var session = login(account(Role.CLIENTE));
        http.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + session.access())).andExpect(status().isOk());
        http.perform(post("/api/v1/auth/logout").with(browserCsrf()).cookie(session.refresh())).andExpect(status().isNoContent());
        http.perform(post("/api/v1/auth/refresh").with(browserCsrf()).cookie(session.refresh())).andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + session.access())).andExpect(status().isUnauthorized());
    }
    @Test void editedTokenCannotGrantAccess() throws Exception {
        var session = login(account(Role.CLIENTE)); String[] pieces = session.access().split("\\.");
        pieces[1] = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"1\",\"role\":\"ADMINISTRADOR\"}".getBytes());
        http.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + String.join(".", pieces))).andExpect(status().isUnauthorized());
    }
    @Test void expiredJwtIsRejected() throws Exception {
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            JwtClaimsSet.builder().issuer("kynex-api").subject("1").audience(List.of("kynex-web"))
                .issuedAt(Instant.now().minusSeconds(600)).expiresAt(Instant.now().minusSeconds(120)).build())).getTokenValue();
        http.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
    }
    @Test void deactivatedAccountCannotUsePreviousToken() throws Exception {
        var user = account(Role.ADMINISTRADOR); var session = login(user); user.active = false; users.saveAndFlush(user);
        http.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + session.access())).andExpect(status().isUnauthorized());
    }
    @Test void currentDatabaseRoleOverridesPreviouslyIssuedToken() throws Exception {
        var user = account(Role.ADMINISTRADOR); var session = login(user); user.role = Role.CLIENTE; users.saveAndFlush(user);
        http.perform(post("/api/v1/products").header("Authorization", "Bearer " + session.access()).contentType(MediaType.APPLICATION_JSON).content(body(productBody()))).andExpect(status().isForbidden());
    }
    @Test void corsRejectsUnconfiguredOrigin() throws Exception {
        http.perform(options("/api/v1/products").header("Origin", "https://untrusted.example.com").header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }
    @Test void corsAllowsConfiguredReactOriginAndCredentials() throws Exception {
        http.perform(options("/api/v1/products").header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "Authorization,Content-Type"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        http.perform(get("/api/v1/products").header("Origin", "http://localhost:5173"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
