package sports.processors;

import sports.IntegrationTest;
import sports.auth.AuthHandler;
import sports.config.ConfigurationHandler;
import sports.core.User;
import sports.core.UserEmail;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import org.assertj.core.api.AssertionsForClassTypes;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.persistence.Tuple;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@QuarkusTest
class UserEmailSenderTest extends IntegrationTest {
    public static final AtomicInteger UniqueNumber = new AtomicInteger();

    @Inject UserEmailSender sender;
    @Inject
    AuthHandler authHandler;
    @Inject JWTParser parser;

    @Test
    void testProcess() throws InterruptedException, MessagingException, IOException, ParseException {
        configureDynamic(Map.of(ConfigurationHandler.Prop.MAIL_TYPE.key, "ses"));

        var user = new User("user+email+sender+test+" + UniqueNumber.incrementAndGet() + "@cytech.gr", "");
        user = ud.persist(user);

        ud.addPendingUserEmail(user, "en", "", UserEmail.ACTION_VERIFY_EMAIL);

        sender.sendUserEmails();

        //now we need to find the email containing the email verification jwt
        var mail = getMail(user.username);
        var html = mail.getContent().toString();

        assertThat(html).contains("verify");

        //extract the email verification jwt from the email
        String jwt = html.split("href")[1].split("\"")[1].replace(config.getEmailVerifyUrl(), "");

        //parse the jwt and verify it
        var parsedVerificationJwt = parser.parse(jwt);
        assertThat(parsedVerificationJwt.getSubject()).isEqualTo(user.username);

        configureDynamic(Map.of(ConfigurationHandler.Prop.MAIL_TYPE.key, "smtp"));
    }

    @Test
    void testUsersEmailMaxTries() {
        configureDynamic(Map.of(ConfigurationHandler.Prop.MAIL_TYPE.key, "ses"));
        configureDynamic(Map.of(ConfigurationHandler.Prop.USER_PROCESSOR_EMAIL_RETRY_INTERVAL.key,"0 minute"));
        configureDynamic(Map.of(ConfigurationHandler.Prop.USER_EMAIL_RETRIES.key, "3"));

        var user = new User("" , "");
        user = ud.persist(user);
        ud.addPendingUserEmail(user, "en", "", UserEmail.ACTION_VERIFY_EMAIL);
        for(var i=0; i<config.getUserMaxEmailTries() ; i++){
            sender.sendUserEmails();
        }
        var pendingUserEmails1 = em.unwrap(Session.class).createNativeQuery("SELECT * from pending_user_emails " +
                "WHERE user_id = :user_id", Tuple.class)
                .setParameter("user_id",user.id)
                .getResultList();
        assertThat(pendingUserEmails1.get(0).get("retries")).isEqualTo(3);

        sender.sendUserEmails();

        var pendingUserEmails2 = em.unwrap(Session.class).createNativeQuery("SELECT * from pending_user_emails WHERE user_id = :user_id", Tuple.class)
                .setParameter("user_id",user.id)
                .getResultList();
        assertThat(pendingUserEmails2).isEmpty();
        configureDynamic(Map.of(ConfigurationHandler.Prop.MAIL_TYPE.key, "smtp"));
    }

    @Test
    void testUsersEmails() throws InterruptedException, MessagingException, IOException, ParseException  {
        configureDynamic(Map.of(ConfigurationHandler.Prop.MAIL_TYPE.key, "ses"));
        configureDynamic(Map.of(ConfigurationHandler.Prop.USER_PROCESSOR_EMAIL_RETRY_INTERVAL.key,"0 minute"));
        configureDynamic(Map.of(ConfigurationHandler.Prop.USER_EMAIL_RETRIES.key, "3"));

        var user = new User("" , "");
        user = ud.persist(user);

        var addedPendingEmail = ud.addPendingUserEmail(user,"en", "", UserEmail.ACTION_VERIFY_EMAIL);
        AssertionsForClassTypes.assertThat(addedPendingEmail).isTrue();

        sender.sendUserEmails();

        var pendingEmails = em.unwrap(Session.class).createNativeQuery("SELECT * from pending_user_emails " +
                "WHERE user_id = :user_id", Tuple.class)
                .setParameter("user_id",user.id)
                .getSingleResult();
        assertThat(pendingEmails.get("retries")).isEqualTo(1);

        user.username = "dev@test.gr";
        user = ud.persist(user);

        sender.sendUserEmails();

        var mail = getMail(user.username);
        var html = mail.getContent().toString();

        assertThat(html).contains("verify");

        //extract the email verification jwt from the email
        String jwt = html.split("href")[1].split("\"")[1].replace(config.getEmailVerifyUrl(), "");

        //parse the jwt and verify it
        var parsedVerificationJwt = parser.parse(jwt);
        assertThat(parsedVerificationJwt.getSubject()).isEqualTo(user.username);

        var emails = em.unwrap(Session.class).createNativeQuery("SELECT * from pending_user_emails " +
                "WHERE user_id = :user_id", Tuple.class)
                .setParameter("user_id",user.id)
                .getResultList();
        assertThat(emails).isEmpty();

        configureDynamic(Map.of(ConfigurationHandler.Prop.MAIL_TYPE.key, "smtp"));
    }

    protected MimeMessage getMail(String rcpt) throws InterruptedException, MessagingException {
        var mailbox = stubs.getSesReceivedRequests();
        AssertionsForInterfaceTypes.assertThat(mailbox).isNotEmpty().hasSize(1);

        MimeMessage mail = mailbox.poll(15, TimeUnit.SECONDS);

        mailbox.clear();
        return mail;
    }
}