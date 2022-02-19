package sports;

import com.fasterxml.jackson.databind.ObjectMapper;
import sports.auth.AuthHandler;
import sports.config.ConfigurationHandler;
import sports.config.DatabaseConfigSource;
import sports.core.User;
import sports.daos.ConfigurationDao;
import sports.daos.UserDao;
import sports.dev.StubServer;
import sports.resources.UserResource;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.ConfigProvider;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Status;
import javax.transaction.Transactional;
import javax.transaction.UserTransaction;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTestResource(IntegrationTest.TimeZoneFixResource.class)
@QuarkusTestResource(IntegrationTest.DbContainerResource.class)
public abstract class IntegrationTest {
    protected Logger logger = LoggerFactory.getLogger(getClass());
    @Inject protected DatabaseConfigSource dbc;
    @Inject protected ConfigurationDao cdao;
    @Inject protected UserDao ud;
    @Inject protected ObjectMapper mapper;
    @Inject protected EntityManager em;
    @Inject protected ConfigurationHandler config;
    @Inject protected StubServer stubs;
    @Inject protected AuthHandler auth;
    @Inject protected MockMailbox mailbox;
    @Inject protected UserTransaction transaction;
    @Inject protected ObjectMapper om;

    @BeforeEach
    public final void abstractBeforeEach() throws Exception {
        if (transaction.getStatus() != Status.STATUS_NO_TRANSACTION) {
            transaction.rollback();
        }
        pauseProcessors();
        truncateUsers();
        stubs.clear();
        mailbox.clear();
    }

    @AfterEach
    public final void abstractAfterEach() throws Exception {
        if (transaction.getStatus() != Status.STATUS_NO_TRANSACTION) {
            transaction.rollback();
        }
        pauseProcessors();
        truncateUsers();
        stubs.clear();
        mailbox.clear();
    }

    @Transactional
    public void truncateUsers() throws Exception {
        em.createNativeQuery("TRUNCATE users cascade").executeUpdate();
    }

    public void pauseProcessors() {
        Map<String, String> pauses = getPauseProperties(true);
        cdao.createOrUpdate(pauses);
        dbc.configReload();
    }

    public void resumeProcessor(String key) {
        cdao.createOrUpdate(Map.of(key, "false"));
        dbc.configReload();
    }

    public Map<String, String> getPauseProperties(boolean pause) {
        return StreamSupport.stream(ConfigProvider.getConfig().getPropertyNames().spliterator(), false)
                .filter(k -> k.endsWith(".pause")).collect(Collectors.toMap(k -> k, k -> Boolean.toString(pause)));
    }

    public static RequestSpecification givenJsonRequest() {
        return given().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON).header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
    }

    public RequestSpecification givenJsonRequest(String username) {
        return givenJsonRequest().header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.generateJwt(0, username));
    }

    public void configureDynamic(Map<String, String> config) {
        cdao.createOrUpdate(config);
        dbc.configReload();
    }

    public UserResource.AccountResponse createUser(String username) throws  Exception{
        UserResource.AccountRequest ar = new UserResource.AccountRequest();
        ar.username = username;
        ar.password = "123qwe";
        ar.lang = "en";

        //request to create a new account
        UserResource.AccountResponse resp = null;
        resp = givenJsonRequest().body(ar).post("/api/users").then()
                .statusCode(200).extract().body().as(UserResource.AccountResponse.class);
        assertThat(resp).isNotNull().usingRecursiveComparison().ignoringExpectedNullFields().isEqualTo(new UserResource.AccountResponse(ar.username, null));

        //make sure the user was persisted in db with status pending
        var user = ud.getByEmail(ar.username).orElseThrow();
        assertThat(user.status).isEqualTo(User.Status.pending);

        // use the extracted email verification jwt to verify the email
        // todo jwt
        resp = givenJsonRequest().header(HttpHeaders.AUTHORIZATION, "Bearer ").put("/api/users/verify-email").then()
                .statusCode(200).extract().body().as(UserResource.AccountResponse.class);
        assertThat(resp).isNotNull().usingRecursiveComparison().ignoringExpectedNullFields().isEqualTo(new UserResource.AccountResponse(ar.username, null));

        user = ud.getByEmail(ar.username).orElseThrow();
        assertThat(user.status).isEqualTo(User.Status.active);

        //email verified, now we should be able to login
        var lr = new UserResource.AccountRequest();
        lr.username = ar.username;
        lr.password = ar.password;

        resp = givenJsonRequest().body(lr).post("/api/users/login").then()
                .statusCode(200).extract().body().as(UserResource.AccountResponse.class);

        return resp;
    }

    protected Mail waitForMailTo(String email) throws Exception {
        for (int i = 0; i < 10; i++) {
            var mails = mailbox.getMessagesSentTo(email);
            if (mails != null && !mails.isEmpty()) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }

        var mails = mailbox.getMessagesSentTo(email);
        var mail = mails.get(0);
        mailbox.clear();
        return mail;
    }

    @Transactional
    public void deleteDynamicConfig(Set<String> keys) {
        em.unwrap(Session.class).createNativeQuery("DELETE FROM configuration WHERE key IN (:keys)")
                .setParameterList("keys", keys)
                .executeUpdate();
        dbc.configReload();
    }

    public static class TimeZoneFixResource implements QuarkusTestResourceLifecycleManager {
        @Override public Map<String, String> start() {TimeZone.setDefault(TimeZone.getTimeZone("UTC"));return Map.of();}
        @Override public void stop() {}
    }

    public static class DbContainerResource implements QuarkusTestResourceLifecycleManager {
        private static final Logger logger = LoggerFactory.getLogger(DbContainerResource.class);
        private static PostgreSQLContainer DB;
        private static final Map<String, String> params = new HashMap<>();

        @Override
        public Map<String, String> start() {
            if (DB == null) {
                DB = new PostgreSQLContainer("postgres:alpine")
                        .withDatabaseName("sports")
                        .withUsername("sports")
                        .withPassword("sports");
                DB.start();
                String url = DB.getJdbcUrl();
                logger.info("Setting datasource url: {}", url);
                params.put("quarkus.datasource.jdbc.url", url);
                params.put("quarkus.datasource.username", "sports");
                params.put("quarkus.datasource.password", "sports");
            }
            return params;
        }

        @Override
        public void stop() {
            if (DB != null) {
                DB.stop();
            }
        }
    }
}
