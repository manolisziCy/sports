package gr.cytech.events.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import gr.cytech.events.config.ConfigurationHandler;
import gr.cytech.events.core.User;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.ses.SesClient;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Properties;

@ApplicationScoped
public class MailDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(MailDispatcher.class);
    private static final String DEFAULT_LANGUAGE = "en";

    @Inject ConfigurationHandler config;
    @Inject SesClient ses;
    @Inject Mailer mailer;

    Session session;

    @PostConstruct
    void init() {
        session = Session.getDefaultInstance(new Properties());
    }

    public void sendVerificationEmail(User user, String verifyEmailJwt) throws Exception {
        String verifyEmailUrl = config.getEmailVerifyUrl();
        if (!verifyEmailUrl.endsWith("/")) {
            verifyEmailUrl += "/";
        }
        verifyEmailUrl += verifyEmailJwt;

        String subj = config.getString("events.email.verify.subject", null);
        String email = config.getString("events.email.verify.template", null)
                .replace("$verifyEmailUrl", verifyEmailUrl);

        String from = config.getEmailVerifySender();
        String to = user.username;

        sendHtmlEmail(from, to, subj, email);
    }

    public void sendResetPasswordEmail(User user, String resetPasswordEmailJwt) throws Exception {
        String resetPasswordEmailUrl = config.getEmailResetPasswordUrl();
        if (!resetPasswordEmailUrl.endsWith("/")) {
            resetPasswordEmailUrl += "/";
        }
        resetPasswordEmailUrl += resetPasswordEmailJwt;
        String subj = config.getString("events.email.reset.password.subject", null);
        String email = config.getString("events.email.reset.password.template", null)
                .replace("$resetPasswordEmailUrl", resetPasswordEmailUrl);
        String from = config.getEmailResetPasswordSender();
        String to = user.username;

        sendHtmlEmail(from, to, subj, email);
    }

    protected void sendHtmlEmail(String from, String to, String subject, String email)
            throws IOException, MessagingException {
        if (config.isMailTypeSmtp()) {
            sendSmtpHtmlEmail(from, to, subject, email);
        } else {
            sendSesHtmlEmail(from, to, subject, email);
        }
    }

    protected void sendSmtpHtmlEmail(String from, String to, String subject, String email) {
        mailer.send(Mail.withHtml(to, subject, email).setFrom(from));
    }

    protected void sendSesHtmlEmail(String from, String to, String subject, String email)
            throws MessagingException, IOException {
        MimeMessage mail = new MimeMessage(session);

        mail.setFrom(!Strings.isNullOrEmpty(from) ? from : "Events <no-reply@audit.cytech.gr>");

        Address[] toAddresses = {new InternetAddress(to)};
        mail.setRecipients(Message.RecipientType.TO, toAddresses);
        mail.setSubject(subject);
        mail.setSentDate(new Date());
        mail.setContent(email, "text/html; charset=UTF-8");

        // Send the email.
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mail.writeTo(outputStream);

        ses.sendRawEmail(req -> req.rawMessage(m -> m.data(SdkBytes.fromByteArray(outputStream.toByteArray()))));
    }
}
