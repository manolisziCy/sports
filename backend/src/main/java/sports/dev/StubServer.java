package sports.dev;

import io.quarkus.arc.profile.UnlessBuildProfile;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.runtime.Startup;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.utils.BinaryUtils;
import software.amazon.awssdk.utils.http.SdkHttpUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Startup
@UnlessBuildProfile("prod")
public class StubServer {
    public static final Logger logger = LoggerFactory.getLogger(StubServer.class);
    public static final int STUB_SERVER_PORT = 9000;
    public static final String PATH_RETRIEVE_STUB_EMAILS = "/retrieve-stub-email";

    @Inject MockMailbox mailbox;
    @Inject Vertx vertx;
    HttpServer server;
    BlockingQueue<MimeMessage> sesReceivedRequests;
    Map<String, Handler<HttpServerRequest>> requestHandlerOverrides;

    @PostConstruct
    void init() {
        requestHandlerOverrides = new HashMap<>();
        sesReceivedRequests = new LinkedBlockingQueue<>();
        server = vertx.createHttpServer(new HttpServerOptions().setPort(STUB_SERVER_PORT))
                .requestHandler(r -> {
                    logger.info("received request {} with params {}", r.path(), r.params());
                    if (requestHandlerOverrides.get(r.path()) != null) {
                        logger.warn("using overriden request handler for path: {}", r.path());
                        requestHandlerOverrides.get(r.path()).handle(r);
                    } else if (r.headers().get("User-Agent").startsWith("aws-sdk")) {
                        handleAwsRequest(r);
                    } else if (r.path().contains(PATH_RETRIEVE_STUB_EMAILS)) {
                        handleRetrieveStubEmail(r);
                    } else {
                        logger.error("unexpected request received: {}", r.path());
                        r.response().setStatusCode(499).end();
                    }
                });
        server.listen();
        logger.error("stub server successfully initialized");
    }

    @PreDestroy
    void destroy() {
        server.close();
        // do not close vertx, as it will completely destroy quarkus live reload :)
        //vertx.close();
    }

    public void clear() {
        sesReceivedRequests.clear();
        requestHandlerOverrides.clear();
    }

    public BlockingQueue<MimeMessage> getSesReceivedRequests() {
        return sesReceivedRequests;
    }

    public Map<String, Handler<HttpServerRequest>> getRequestHandlerOverrides() {
        return requestHandlerOverrides;
    }

    public void handleAwsRequest(HttpServerRequest r) {
        r.bodyHandler(b -> {
            String body = b.toString();
            if (body.contains("SendRawEmail")) {
                String base64Body = SdkHttpUtils.urlDecode(body.split("RawMessage.Data=")[1]);
                MimeMessage mail = null;
                try {
                    mail = new MimeMessage(Session.getDefaultInstance(new Properties()),
                            new ByteArrayInputStream(BinaryUtils.fromBase64(base64Body)));
                    logger.info("received: {}", mail.getContent().toString());
                } catch (MessagingException | IOException e) {
                    logger.error("an exception caught while handling the aws request", e);
                }
                sesReceivedRequests.add(mail);
                r.response().setStatusCode(200).end(getSendRawEmailResponse());
            }
        });
    }

    public void handleRetrieveStubEmail(HttpServerRequest r) {
        vertx.executeBlocking(promise -> {
            final String recipient = r.getParam("email");
            long startTime = System.currentTimeMillis();
            while ((System.currentTimeMillis() - startTime) < 10_000) {
                try {
                    var mails = mailbox.getMessagesSentTo(recipient);
                    if (mails == null || mails.isEmpty()) {
                        TimeUnit.MILLISECONDS.sleep(10);
                    } else {
                        var mail = mails.get(0);
                        mailbox.clear();
                        var body = mail.getHtml();
                        var json = "{\"email\": \"" + body + "\"}";
                        promise.complete(json);
                        return;
                    }
                } catch (Exception e) {
                    logger.warn("Exception getting mock mails", e);
                }
            }
        }, res -> {
            if (res.result() == null) {
                r.response().setStatusCode(400).end();
            } else {
                var json = res.result().toString();
                logger.info("sending back stub email json: {}", json);
                r.response().setStatusCode(200).end(Json.encode(Map.of("email", json)));
            }
        });
    }

    public static String getVerifyEmailIdentityResponse() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
               "<VerifyEmailAddressResponse xmlns=\"http://ses.amazonaws.com/doc/2010-12-01/\">" +
                   "<ResponseMetadata>" +
                       "<RequestId>" + UUID.randomUUID() + "</RequestId>" +
                   "</ResponseMetadata>" +
               "</VerifyEmailAddressResponse>";
    }

    public static String getSendRawEmailResponse() {
        return  "<SendEmailResponse xmlns=\"https://email.amazonaws.com/doc/2010-03-31/\">" +
                    "<SendEmailResult>" +
                        "<MessageId>" + UUID.randomUUID() + "</MessageId>" +
                    "</SendEmailResult>" +
                    "<ResponseMetadata>" +
                        "<RequestId>" + UUID.randomUUID() + "</RequestId>" +
                    "</ResponseMetadata>" +
                "</SendEmailResponse>";
    }
}
