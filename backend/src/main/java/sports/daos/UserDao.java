package sports.daos;

import com.google.common.base.Strings;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sports.config.ConfigurationHandler;
import sports.core.User;
import sports.core.UserEmail;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Tuple;
import javax.transaction.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class UserDao {
    private static final Logger logger = LoggerFactory.getLogger(UserDao.class);

    @Inject EntityManager em;
    @Inject ConfigurationHandler config;

    @Transactional
    public Optional<User> getByEmail(String email) {
        try {
            List<Tuple> tl = em.createNativeQuery(
                    "SELECT * FROM users WHERE username = :email", Tuple.class)
                    .setParameter("email", Strings.nullToEmpty(email).toLowerCase())
                    .getResultList();
            if (tl == null || tl.isEmpty()) {
                return Optional.empty();
            }
            var t = tl.get(0);
            var usr = fromTuple(t);
            usr.password = t.get("password", String.class);

            return Optional.of(usr);
        } catch (Exception e) {
            logger.warn("error finding user by email: {}", email, e);
            return Optional.empty();
        }
    }

    @Transactional(value = Transactional.TxType.REQUIRED)
    public User persist(User user) {
        try {
            final boolean insert = user.id <= 0;
            Map<String, Object> params;
            String query;
            if (insert) {
                query = "INSERT INTO users (username, password) " +
                        "VALUES (:un, :pwd)" +
                        " RETURNING id";
                params = Map.of();
            } else {
                query = "UPDATE users set username=:un,password=:pwd" +
                        " WHERE id = :id RETURNING id";
                params = Map.of("id", user.id);
            }

            Tuple t = em.unwrap(Session.class).createNativeQuery(query, Tuple.class)
                    .setParameter("un", Strings.nullToEmpty(user.username).toLowerCase().trim())
                    .setParameter("pwd", user.password)
                    .setProperties(params)
                    .getSingleResult();
            user.id = t.get("id", Long.class);
            return user;
        } catch (Exception e) {
            logger.warn("error persisting user: {}", user, e);
            return null;
        }
    }

    @Transactional(value = Transactional.TxType.REQUIRED)
    public User updatePasswordAndStatus(User user) {
        try {
            em.createNativeQuery("UPDATE users SET password=:pwd, status=:status WHERE username=:un")
                    .setParameter("un", user.username)
                    .setParameter("pwd", user.password)
                    .setParameter("status", user.status.val)
                    .executeUpdate();
            return user;
        } catch (Exception e) {
            logger.warn("error updating user password: {}", user.username, e);
            return null;
        }
    }

    @Transactional
    public boolean updateLastLogin(User user) {
        try {
            em.createNativeQuery("UPDATE users SET last_login_at = now() WHERE id=:id")
                    .setParameter("id", user.id)
                    .executeUpdate();
            return true;
        } catch (Exception e) {
            logger.warn("error updating user's last login time {}", user.username, e);
            return false;
        }
    }

    @Transactional
    public boolean addPendingUserEmail(User user, String lang, String actor, String action) {
        try {
            em.createNativeQuery("INSERT INTO pending_user_emails (user_id, lang, next_try, retries, actor, action) " +
                            "VALUES (:id, :lang, now(), 0, :actor, :action) ON CONFLICT DO NOTHING")
                    .setParameter("id", user.id)
                    .setParameter("lang", Strings.isNullOrEmpty(lang) ? "en" : lang)
                    .setParameter("actor", actor)
                    .setParameter("action", action)
                    .executeUpdate();
            return true;
        } catch (Exception e) {
            logger.warn("error inserting pending email for user: {}", user, e);
            return false;
        }
    }

    @Transactional(value = Transactional.TxType.REQUIRED)
    public User updateEmailVerified(User user) {
        try {
            em.createNativeQuery("UPDATE users SET email_verified_at=now(), status=:status WHERE username=:un")
                    .setParameter("un", user.username)
                    .setParameter("status", User.Status.active.val)
                    .executeUpdate();
            user.status = User.Status.active;
            return user;
        } catch (Exception e) {
            logger.warn("error updating user for email verified: {}", user, e);
            return null;
        }
    }

    @Transactional(value = Transactional.TxType.REQUIRED)
    public boolean updateEmailVerificationResent(User user) {
        try {
            return em.createNativeQuery("UPDATE users SET email_verification_sent_at=now() " +
                            "WHERE username=:un AND email_verification_sent_at < now() - CAST (:interval AS INTERVAL)")
                    .setParameter("un", user.username)
                    .setParameter("interval", config.getMaxThrottling())
                    .executeUpdate() == 1;
        } catch (Exception e) {
            logger.warn("error updating user to set email verification send time {}", user, e);
            return false;
        }
    }

    @Transactional(value = Transactional.TxType.REQUIRED)
    public boolean updatePasswordResetSent(User user) {
        try {
            return em.createNativeQuery("UPDATE users SET password_reset_email_sent_at=now() " +
                            "WHERE username=:un AND " +
                            "(password_reset_email_sent_at IS NULL OR " +
                            "password_reset_email_sent_at < now() - CAST (:interval AS INTERVAL))")
                    .setParameter("un", user.username)
                    .setParameter("interval", config.getMaxThrottling())
                    .executeUpdate() == 1;
        } catch (Exception e) {
            logger.warn("error updating user password_reset_email_sent_at {}", user.username, e);
            return false;
        }
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public List<UserEmail> getPendingUserEmail() {
        return em.unwrap(Session.class).createNativeQuery(
                "SELECT u.*, pe.user_id, pe.lang,pe.next_try, pe.retries, pe.actor, pe.action " +
                        "FROM pending_user_emails pe " +
                        "JOIN users u ON (pe.user_id = u.id) " +
                        "WHERE next_try < now() LIMIT :limit " +
                        "FOR UPDATE OF pe SKIP LOCKED", Tuple.class)
                .setParameter("limit", config.getEmailUserLimit())
                .getResultStream()
                .map(this::userEmailFromTuple)
                .collect(Collectors.toList());
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public boolean updatePendingUserEmailResults(Collection<Long> success, HashMap<Long, Integer> fail) {
        if (success != null && !success.isEmpty()) {
            em.unwrap(Session.class).createNativeQuery(
                    "DELETE FROM pending_user_emails WHERE user_id IN (:ids)")
                    .setParameterList("ids", success)
                    .executeUpdate();
        }
        if (fail != null && !fail.isEmpty()) {

            var updateList = fail.entrySet().stream()
                    .filter(entry -> entry.getValue() < config.getUserMaxEmailTries())
                    .map(el -> el.getKey()).collect(Collectors.toList());

            var deleteList = fail.entrySet().stream()
                    .filter(entry -> entry.getValue() >= config.getUserMaxEmailTries())
                    .map(el -> el.getKey()).collect(Collectors.toList());

            if (!updateList.isEmpty()) {
                em.unwrap(Session.class).createNativeQuery(
                        "UPDATE pending_user_emails SET next_try = now() + CAST(:interval AS INTERVAL) , " +
                                "retries = retries + 1 WHERE user_id IN (:ids)")
                        .setParameterList("ids", updateList)
                        .setParameter("interval", config.getProcessorUserEmailRetryInterval(false))
                        .executeUpdate();
            }

            if (!deleteList.isEmpty()) {
                em.unwrap(Session.class).createNativeQuery("DELETE FROM pending_user_emails WHERE user_id IN (:ids)")
                        .setParameterList("ids", deleteList)
                        .executeUpdate();
            }

        }
        return true;
    }

    @Transactional(value = Transactional.TxType.REQUIRED)
    public User delete(User user) {
        try {
            em.createNativeQuery("DELETE FROM users WHERE id=:id")
                    .setParameter("id", user.id)
                    .executeUpdate();
            return user;
        } catch (Exception e) {
            logger.warn("error deleting user: {}", user, e);
            return null;
        }
    }

    public UserEmail userEmailFromTuple(Tuple t) {
        var email = new UserEmail();
        email.user = fromTuple(t);
        email.lang = t.get("lang", String.class);
        email.actor = t.get("actor", String.class);
        email.action = t.get("action", String.class);
        email.retries = t.get("retries", Integer.class);
        return email;
    }

    public User fromTuple(Tuple t) {
        var user = new User();
        user.id = t.get("id", Number.class).longValue();
        user.username = t.get("username", String.class);
        user.status = User.Status.from(t.get("status", Number.class).intValue());
        user.createdAt = t.get("created_at", Instant.class);
        user.lastLogin = t.get("last_login_at", Instant.class);
        return user;
    }
}
