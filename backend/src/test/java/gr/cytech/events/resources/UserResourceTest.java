package gr.cytech.events.resources;

import gr.cytech.events.IntegrationTest;
import gr.cytech.events.config.ConfigurationHandler;
import gr.cytech.events.core.User;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
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

        //now we need to find the email containing the email verification jwt
        var mail = waitForMailTo(ar.username);
        var html = mail.getHtml();
        assertThat(html).contains("verify");
        //extract the email verification jwt from the email
        String jwt = html.split("href")[1].split("\"")[1].replace(config.getEmailVerifyUrl(), "");

        //parse the jwt and verify it
        var parsedVerificationJwt = parser.parse(jwt);
        assertThat(parsedVerificationJwt.getSubject()).isEqualTo(ar.username);

        //make sure the user was persisted in db with status pending
        var user = ud.getByEmail(ar.username).orElseThrow();
        assertThat(user.status).isEqualTo(User.Status.pending);

        // use the extracted email verification jwt to verify the email
        resp = givenJsonRequest().header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt).put("/api/users/verify-email").then()
                .statusCode(200).extract().body().as(UserResource.AccountResponse.class);
        assertThat(resp).isNotNull().usingRecursiveComparison().ignoringExpectedNullFields().isEqualTo(new UserResource.AccountResponse(ar.username, null));

        user = ud.getByEmail(ar.username).orElseThrow();
        assertThat(user.status).isEqualTo(User.Status.active);

        //email verified, now we should be able to login
        var lr = new UserResource.AccountRequest();
        lr.username = ar.username;
        lr.password = ar.password;

        loginAndVerifyResponse(lr);

        //email is case insensitive. Lets check an uppercase variant of user's email
        lr.username = lr.username.toUpperCase();
        loginAndVerifyResponse(lr);
    }

    @Test
    public void testResetPassword() throws Exception {
        //insert user
        User user = new User("devs+test@cytech.gr", auth.hashPassword("123qwe"));
        user.status = User.Status.active;
        user = ud.persist(user);

        //request to reset password
        var ar = new UserResource.AccountRequest();
        ar.username = user.username;
        var resp = givenJsonRequest().body(ar).post("/api/users/reset-password").then()
                .statusCode(200).extract().body().as(UserResource.AccountResponse.class);
        assertThat(resp).isNotNull().usingRecursiveComparison().ignoringExpectedNullFields().isEqualTo(new UserResource.AccountResponse(ar.username, null));

        //now we need to find the email containing the password reset jwt
        var mail = waitForMailTo(ar.username);
        var html = mail.getHtml();
        assertThat(html).contains("reset");
        //extract the email verification jwt from the email
        String jwt = html.split("href")[1].split("\"")[1].replace(config.getEmailResetPasswordUrl(), "");

        //parse the jwt and verify it
        var parsedVerificationJwt = parser.parse(jwt);
        assertThat(parsedVerificationJwt.getSubject()).isEqualTo(ar.username);

        //make sure the user was persisted in db with the reset password email tstamp updated
        boolean ok = (boolean) em.createNativeQuery(
                "SELECT TRUE FROM users WHERE username=:un")
                .setParameter("un", ar.username)
                .getSingleResult();
        assertThat(ok).isTrue();

        // use the extracted password reset jwt to reset the password
        ar = new UserResource.AccountRequest();
        ar.username = user.username;
        ar.password = "123qwer";
        resp = givenJsonRequest().header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .body(ar).put("/api/users/reset-password").then()
                .statusCode(200).extract().body().as(UserResource.AccountResponse.class);
        assertThat(resp).isNotNull().usingRecursiveComparison().ignoringExpectedNullFields().isEqualTo(new UserResource.AccountResponse(ar.username, null));

        //password reset, now we should be able to login with the new password
        var lr = new UserResource.AccountRequest();
        lr.username = ar.username;
        lr.password = ar.password;

        loginAndVerifyResponse(lr);
    }

    @Test
    public void testVerificationEmailFallback() throws Exception {
        UserResource.AccountRequest ar = new UserResource.AccountRequest();
        ar.username = "devs+test@cytech.gr";
        ar.password = "123qwe";
        ar.lang = "it";

        givenJsonRequest().body(ar).post("/api/users").then()
                .statusCode(200).extract().body().as(UserResource.AccountResponse.class);

        var mail = waitForMailTo(ar.username);
        var html = mail.getHtml();

        UserResource.AccountRequest ar2 = new UserResource.AccountRequest();
        ar2.username = "dev+test@cytech.gr";
        ar2.password = "123qwe";
        ar2.lang = "it";

        givenJsonRequest().body(ar2).post("/api/users").then()
                .statusCode(200).extract().body().as(UserResource.AccountResponse.class);

        mail = waitForMailTo(ar2.username);
        html = mail.getHtml();
    }

    @Test
    public void testReSendVerificationEmail() throws Exception {
        UserResource.AccountRequest ar = new UserResource.AccountRequest();
        ar.username = "devs+test@cytech.gr";
        ar.password = "123qwe";
        ar.lang = "en";

        //request to create a new account
        var resp = givenJsonRequest().body(ar).post("/api/users").then()
                .statusCode(200).extract().body().as(UserResource.AccountResponse.class);
        assertThat(resp).isNotNull().usingRecursiveComparison().ignoringExpectedNullFields().isEqualTo(new UserResource.AccountResponse(ar.username, null));

        //now we need to find the email containing the email verification jwt
        var mail = waitForMailTo(ar.username);
        var html = mail.getHtml();
        //extract the email verification jwt from the email
        var jwt = extractVerificationJwtFromEmail(html);

        //make sure the user was persisted in db with status pending
        var user = ud.getByEmail(ar.username).orElseThrow();
        assertThat(user.status).isEqualTo(User.Status.pending);

        //make sure that the user cannot ask to resend verification email immediately
        var resendReq = new UserResource.AccountRequest();
        resendReq.username = ar.username;
        resendReq.password = ar.password;
        var resendError = givenJsonRequest().body(resendReq).put("/api/users/resend-verification-email").then()
                .statusCode(400).extract().body().as(RequestValidator.ValidationError.class);
        assertThat(resendError.error).isEqualTo(RequestValidator.ERROR_THROTTLING);

        // change the last verification email submit time further in the past
        updateVerificationSubmitTime(resendReq.username);

        //repeat the request, it should succeed
        var resendResp = givenJsonRequest().body(resendReq).put("/api/users/resend-verification-email").then()
                .statusCode(200).extract().body().as(UserResource.AccountResponse.class);
        assertThat(resendResp.username).isEqualTo(resendReq.username);

        //now we need to find the re-sent email containing the email verification jwt
        mail = waitForMailTo(ar.username);
        html = mail.getHtml();
        //extract the email verification jwt from the email
        jwt = extractVerificationJwtFromEmail(html);

        // use the extracted email verification jwt to verify the email
        resp = givenJsonRequest().header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt).put("/api/users/verify-email").then()
                .statusCode(200).extract().body().as(UserResource.AccountResponse.class);
        assertThat(resp).isNotNull().usingRecursiveComparison().ignoringExpectedNullFields().isEqualTo(new UserResource.AccountResponse(ar.username, null));

        user = ud.getByEmail(ar.username).orElseThrow();
        assertThat(user.status).isEqualTo(User.Status.active);
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

    protected void loginAndVerifyResponse(UserResource.AccountRequest lr) throws ParseException {
        var preLoginTstamp = getLastLoginTimestamp(lr.username);
        UserResource.AccountResponse resp;
        resp = givenJsonRequest().body(lr).post("/api/users/login").then()
                .statusCode(200).extract().body().as(UserResource.AccountResponse.class);

        assertThat(resp).isNotNull();
        assertThat(resp.username).isEqualToIgnoringCase(lr.username);
        assertThat(resp.token).isNotEmpty();
        var parsedJwt = parser.parse(resp.token);
        assertThat(parsedJwt).isNotNull();
        assertThat(parsedJwt.getSubject()).isEqualToIgnoringCase(lr.username);
        var postLoginTstamp = getLastLoginTimestamp(lr.username);
        if (preLoginTstamp != null) {
            assertThat(postLoginTstamp).isAfter(preLoginTstamp);
        }
    }

    protected String extractVerificationJwtFromEmail(String email) {
        assertThat(email).contains("verify");
        //extract the email verification jwt from the email
        return email.split("href")[1].split("\"")[1].replace(config.getEmailVerifyUrl(), "");
    }

    @Transactional
    public Instant getLastLoginTimestamp(String username) {
        return (Instant) em.createNativeQuery("SELECT last_login_at FROM users where username=:username")
                .setParameter("username", username.toLowerCase()).getSingleResult();
    }

    @Transactional
    public void updateVerificationSubmitTime(String username) {
        em.createNativeQuery("UPDATE users SET email_verification_sent_at=now()-CAST('11 minutes' AS INTERVAL) WHERE username=:username")
                .setParameter("username", username).executeUpdate();
    }
}
