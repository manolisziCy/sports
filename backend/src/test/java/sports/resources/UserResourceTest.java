package sports.resources;

import sports.IntegrationTest;
import sports.config.ConfigurationHandler;
import sports.core.User;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.core.HttpHeaders;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class UserResourceTest extends IntegrationTest {
    @Inject JWTParser parser;

    @BeforeEach
    public void setup() {
        resumeProcessor(ConfigurationHandler.Prop.USER_PROCESSOR_EMAIL_PAUSE.key);
    }

    @Test
    public void testRegisterLogin() throws Exception {
        UserResource.AccountRequest ar = new UserResource.AccountRequest();
        ar.username = "devs+test@cytech.gr";
        ar.password = "123qwe";
        ar.lang = "en";

        //request to create a new account
        var resp = givenJsonRequest().body(ar).post("/api/users").then()
                .statusCode(200).extract().body().as(UserResource.AccountResponse.class);
        assertThat(resp).isNotNull().usingRecursiveComparison().ignoringExpectedNullFields().isEqualTo(new UserResource.AccountResponse(ar.username, null));
    }

    @Test
    public void jwtRefresh() throws Exception {
        final String email = "test@cytech.gr";
        final List<String> eps = List.of("e1", "e2");
        final List<String> trs = List.of("EN", "GR");
        final boolean canUpdateStatus = true;
        final boolean canEdit = true;
        final boolean canAdd = true;
        final boolean isDeletable = true;
        var resp = givenJsonRequest(email).post("/api/users/refresh").then()
                .statusCode(200).extract().body().as(UserResource.AccountResponse.class);
        assertThat(resp).isNotNull();
        assertThat(resp.username).isEqualTo(email);
        assertThat(resp.token).isNotBlank();

        var parsedJwt = parser.parse(resp.token);
        assertThat(parsedJwt).isNotNull();
        assertThat(parsedJwt.getSubject()).isEqualToIgnoringCase(email);
    }

    @Transactional
    public Instant getLastLoginTimestamp(String username) {
        return (Instant) em.createNativeQuery("SELECT last_login_at FROM users where username=:username")
                .setParameter("username", username.toLowerCase()).getSingleResult();
    }
}
