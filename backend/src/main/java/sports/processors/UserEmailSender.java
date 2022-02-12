package sports.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sports.auth.AuthHandler;
import sports.config.ConfigurationHandler;
import sports.core.Event;
import sports.core.Event.EventName;
import sports.core.UserEmail;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class UserEmailSender extends AbstractProcessor {
    private static final Logger logger = LoggerFactory.getLogger(UserEmailSender.class);

    @Inject AuthHandler authHandler;
    @Inject ObjectMapper mapper;

    @Override
    public String getPauseConfigKey() {
        return ConfigurationHandler.Prop.USER_PROCESSOR_EMAIL_PAUSE.key;
    }

    @Override
    public String getThreadsConfigKey() {
        return ConfigurationHandler.Prop.USER_PROCESSOR_EMAIL_THREADS.key;
    }

    @Override
    public String getSleepMsConfigKey() {
        return ConfigurationHandler.Prop.USER_PROCESSOR_EMAIL_SLEEP.key;
    }

    @Override
    public String getTpsConfigKey() {
        return ConfigurationHandler.Prop.USER_PROCESSOR_EMAIL_TPS.key;
    }

    @Override
    public boolean process() {
        try {
            return sendUserEmails();
        } catch (Exception e) {
            logger.warn("error account emails", e);
            return false;
        }
    }

    @Transactional
    public boolean sendUserEmails() {
        var dao = udao.get();
        var mailer = imd.get();
        List<UserEmail> emails = dao.getPendingUserEmail();
        if (emails.isEmpty()) {
            return false;
        }
        List<Long> success = new ArrayList<>();
        HashMap<Long,Integer> fail = new HashMap<>();
        for (UserEmail email : emails) {
            long userId = email.user.id;
            try {
                acquireTpsPermit(1);
                String token = authHandler.generateJwt(userId, email.user.username);
                EventName eventName = null;
                switch (email.action) {
                    case UserEmail.ACTION_RESET_PASSWORD:
                        eventName = EventName.ProcessResetPasswordEmail;
                        mailer.sendResetPasswordEmail(email.user, token);
                        break;
                    case UserEmail.ACTION_VERIFY_EMAIL:
                        eventName = EventName.ProcessVerifyEmail;
                        mailer.sendVerificationEmail(email.user, token);
                        break;
                    default:
                        logger.warn("unknown token role for user email{}", email);
                }
                success.add(userId);

                if (eventName != null) {
                    var event = Event.create(eventName).users(email.actor, email.user.username)
                            .success(Map.of("userId", userId, "action", Strings.nullToEmpty(email.action)));
                    logger.info("{}", event.toJson(mapper));
                } else {
                    var event = Event.create(EventName.ProcessUserEmail)
                            .users(email.actor, email.user.username)
                            .error("UnsupportedEmailType", Map.of("userId", userId,
                                    "action", Strings.nullToEmpty(email.action)));
                    logger.error("{}", event.toJson(mapper));
                }
            } catch (Exception e) {
                var username = email.user != null ? Strings.nullToEmpty(email.user.username) : "";
                var event = Event.create(EventName.ProcessUserEmail)
                        .users(email.actor, username)
                        .error("UserEmailDispatchError", Map.of("email", email, "retries",
                                email.retries));
                logger.error("{}", event.toJson(mapper), e);
                fail.put(userId,email.retries);
            }
        }

        dao.updatePendingUserEmailResults(success, fail);

        return !emails.isEmpty();
    }
}
