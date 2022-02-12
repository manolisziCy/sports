package sports.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import io.quarkus.security.UnauthorizedException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sports.auth.AuthHandler;
import sports.config.ConfigurationHandler;
import sports.core.Event;
import sports.core.User;
import sports.core.UserEmail;
import sports.daos.UserDao;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Map;

@Path("/users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class UserResource extends GenericResource {
    private static final Logger logger = LoggerFactory.getLogger(UserResource.class);

    @Inject
    ConfigurationHandler config;
    @Inject RequestValidator validator;
    @Inject AuthHandler authHandler;
    @Inject
    UserDao ud;
    @Inject JsonWebToken jwt;

    @POST
    @PermitAll
    public AccountResponse createAccount(AccountRequest ar) {
        return createOrUpdateAccount(ar);
    }

    @DELETE
    @Path("/me")
    @Transactional
    @RolesAllowed("**")
    public AccountResponse deleteMyAccount() {
        if (AuthHandler.hasInvalidSubject(jwt)) {
            var event = event(Event.EventName.DeleteAccount)
                    .users(jwt.getSubject(), jwt.getSubject())
                    .error("ActionNotAllowed", null);
            logger.warn("{}", event.toJson(mapper));
            throw new UnauthorizedException();
        }
        var user = ud.getByEmail(jwt.getSubject()).orElseThrow(() -> {
            var event = event(Event.EventName.DeleteAccount)
                    .users(jwt.getSubject(), jwt.getSubject())
                    .error("AccountNotFound", Map.of("email", jwt.getSubject()));
            logger.warn("{}", event.toJson(mapper));
            return new UnauthorizedException();
        });
        ud.delete(user);
        var event = event(Event.EventName.DeleteAccount)
                .users(jwt.getSubject(), jwt.getSubject())
                .success(Map.of("email", jwt.getSubject()));
        logger.info("{}", event.toJson(mapper));
        return new AccountResponse(user.username, null);
    }

    @POST
    @Path("/login")
    @PermitAll
    public AccountResponse login(AccountRequest lr) throws JsonProcessingException {
        if (lr == null || Strings.isNullOrEmpty(lr.username) || Strings.isNullOrEmpty(lr.password)) {
            var username = lr != null ? lr.username : null;
            var event = event(Event.EventName.Login)
                    .users(username, username)
                    .error("AccountNotFound", Map.of("email", Strings.nullToEmpty(username)));
            logger.warn("{}", event.toJson(mapper));
            throw new UnauthorizedException();
        }
        normalize(lr);
        var user = ud.getByEmail(lr.username).orElseThrow(() -> {
            var event = event(Event.EventName.Login)
                    .users(lr.username, lr.username)
                    .error("AccountNotFound", Map.of("email", Strings.nullToEmpty(lr.username)));
            logger.warn("{}", event.toJson(mapper));
            return new UnauthorizedException();
        });

        if (user.status != User.Status.active) {
            var event = event(Event.EventName.Login)
                    .users(user.username, lr.username)
                    .error("InactiveAccount", Map.of("status", user.status.name()));
            logger.warn("{}", event.toJson(mapper));
            if (user.status == User.Status.pending) {
                throw validator.createError(RequestValidator.ERROR_INVALID_USER_STATUS, "status", "status is pending");
            }
            throw new UnauthorizedException();
        }

        if (!auth.checkPassword(lr.password, user.password)) {
            var event = event(Event.EventName.Login)
                    .users(user.username, lr.username)
                    .error("InvalidPassword", Map.of("email", Strings.nullToEmpty(lr.username)));
            logger.warn("{}", event.toJson(mapper));
            throw new UnauthorizedException();
        }

        ud.updateLastLogin(user);

        var event =  event(Event.EventName.Login)
                .users(user.username, lr.username)
                .success(Map.of("id", String.valueOf(user.id)));
        logger.info("{}", event.toJson(mapper));
        return new AccountResponse(user.username, auth.generateJwt(user.id, user.username));
    }

    @POST
    @Path("/refresh")
    @RolesAllowed("**")
    public AccountResponse refresh() {
        if (AuthHandler.hasInvalidSubject(jwt)) {
            var event =  event(Event.EventName.RefreshToken)
                    .error("ActionNotAllowed", null);
            logger.warn("{}", event.toJson(mapper));
            throw new UnauthorizedException();
        }

        var resp = new AccountResponse();
        resp.username = jwt.getSubject();
        resp.token = auth.generateJwt(jwt);
        return resp;
    }

    @PUT
    @Path("/verify-email")
    @RolesAllowed("**")
    public AccountResponse verifyEmail() {
        if (AuthHandler.hasInvalidSubject(jwt)) {
            var event =  event(Event.EventName.VerifyEmail)
                    .error("ActionNotAllowed", null);
            logger.warn("{}", event.toJson(mapper));
            throw new UnauthorizedException();
        }
        var user = ud.getByEmail(jwt.getSubject()).orElseThrow(() -> {
            var event =  event(Event.EventName.VerifyEmail)
                    .users(null, null)
                    .error("AccountNotFound", null);
            logger.warn("{}", event.toJson(mapper));
            return new UnauthorizedException();
        });

        if (user.status != User.Status.pending) {
            var event =  event(Event.EventName.VerifyEmail)
                    .users(user.username, user.username)
                    .error("AccountAlreadyActive", Map.of("status", String.valueOf(user.status)));
            logger.warn("{}", event.toJson(mapper));
            throw validator.createError(RequestValidator.ERROR_INVALID_USER_STATUS, "status",
                    "status is not pending email verification");
        }

        ud.updateEmailVerified(user);

        var event = event(Event.EventName.VerifyEmail)
                .users(user.username, user.username)
                .success(Map.of("id", String.valueOf(user.id)));
        logger.info("{}", event.toJson(mapper));

        return new AccountResponse(user.username, null);
    }

    @PUT
    @Path("/resend-verification-email")
    public AccountResponse resendVerificationEmail(AccountRequest ar) {
        if (ar == null || Strings.isNullOrEmpty(ar.username) || Strings.isNullOrEmpty(ar.password)) {
            var username = ar != null ? ar.username : "";
            var event =  event(Event.EventName.SendVerificationEmail)
                    .users(jwt.getSubject(), username)
                    .error("ActionNotAllowed", Map.of("username", Strings.nullToEmpty(username)));
            logger.warn("{}", event.toJson(mapper));
            throw new UnauthorizedException();
        }
        normalize(ar);

        var user = ud.getByEmail(ar.username).orElseThrow(() -> {
            var event =  event(Event.EventName.SendVerificationEmail)
                    .users(jwt.getSubject(), null)
                    .error("AccountNotFound", Map.of("username", Strings.nullToEmpty(ar.username)));
            logger.warn("{}", event.toJson(mapper));
            return new UnauthorizedException();
        });

        if (!auth.checkPassword(ar.password, user.password)) {
            var event =  event(Event.EventName.SendVerificationEmail)
                    .users(jwt.getSubject(), user.username)
                    .error("InvalidPassword", Map.of("username", Strings.nullToEmpty(ar.username)));
            logger.warn("{}", event.toJson(mapper));
            throw new UnauthorizedException();
        }

        if (user.status != User.Status.pending) {
            var event =  event(Event.EventName.SendVerificationEmail)
                    .users(jwt.getSubject(), user.username)
                    .error("AccountAlreadyActive", Map.of("username", Strings.nullToEmpty(ar.username),
                            "status", String.valueOf(user.status)));
            logger.warn("{}", event.toJson(mapper));
            throw validator.createError(RequestValidator.ERROR_INVALID_USER_STATUS, "status",
                    "status is not pending email verification");
        }

        boolean updated = ud.updateEmailVerificationResent(user);
        if (!updated) {
            var event = event(Event.EventName.SendVerificationEmail)
                    .users(jwt.getSubject(), user.username)
                    .error("VerificationEmailThrottling", Map.of("username",
                            Strings.nullToEmpty(ar.username)));
            logger.warn("{}", event.toJson(mapper));
            throw validator.createError(RequestValidator.ERROR_THROTTLING, "email",
                    "error resending verification email so soon");
        }

        var emailScheduled = ud.addPendingUserEmail(user, ar.lang, jwt.getSubject(),
                UserEmail.ACTION_VERIFY_EMAIL);
        if (!emailScheduled) {
            var event = event(Event.EventName.SendVerificationEmail)
                    .users(jwt.getSubject(), user.username)
                    .error("EmailNotSent", Map.of("username", Strings.nullToEmpty(ar.username)));
            logger.warn("{}", event.toJson(mapper));
            throw validator.createError(RequestValidator.ERROR_EMAIL_DISPATCH, "email",
                    "error resending verification email");
        }
        var event = event(Event.EventName.SendVerificationEmail)
                .users(jwt.getSubject(), user.username)
                .success(Map.of("id", String.valueOf(user.id)));
        logger.info("{}", event.toJson(mapper));
        return new AccountResponse(user.username, null);
    }

    @POST
    @Path("/reset-password")
    public AccountResponse sendResetPasswordEmail(AccountRequest ar) throws IOException, MessagingException {
        if (ar == null || Strings.isNullOrEmpty(ar.username)) {
            var username = ar != null ? ar.username : "";
            var event =  event(Event.EventName.SendResetPasswordEmail)
                    .users(jwt.getSubject(), username)
                    .error("ActionNotAllowed", Map.of("username", Strings.nullToEmpty(username)));
            logger.warn("{}", event.toJson(mapper));
            throw new UnauthorizedException();
        }
        normalize(ar);

        var user = ud.getByEmail(ar.username).orElseThrow(() -> {
            var event =  event(Event.EventName.SendResetPasswordEmail)
                    .users(jwt.getSubject(), ar.username)
                    .error("AccountNotFound", Map.of("username", Strings.nullToEmpty(ar.username)));
            logger.warn("{}", event.toJson(mapper));
            return new UnauthorizedException();
        });

        if (user.status == User.Status.suspended) {
            var event =  event(Event.EventName.SendResetPasswordEmail)
                    .users(jwt.getSubject(), user.username)
                    .error("AccountStatusSuspended", Map.of("username", Strings.nullToEmpty(ar.username),
                            "status", String.valueOf(user.status)));
            logger.warn("{}", event.toJson(mapper));
            throw new UnauthorizedException();
        }

        boolean updated = ud.updatePasswordResetSent(user);
        if (!updated) {
            var event = event(Event.EventName.SendResetPasswordEmail)
                    .users(jwt.getSubject(), user.username)
                    .error("ResetPasswordEmailThrottling", Map.of("username",
                            Strings.nullToEmpty(ar.username)));
            logger.warn("{}", event.toJson(mapper));
            throw validator.createError(RequestValidator.ERROR_THROTTLING, "email",
                    "error sending password reset email so soon");
        }

        var emailScheduled = ud.addPendingUserEmail(user, ar.lang, jwt.getSubject(),
                UserEmail.ACTION_RESET_PASSWORD);
        if (!emailScheduled) {
            var event = event(Event.EventName.SendResetPasswordEmail)
                    .users(jwt.getSubject(), user.username)
                    .error("EmailNotSent", Map.of("username", Strings.nullToEmpty(ar.username)));
            logger.warn("{}", event.toJson(mapper));
            throw validator.createError(RequestValidator.ERROR_EMAIL_DISPATCH, "email",
                    "error sending password reset email");
        }
        var event = event(Event.EventName.SendResetPasswordEmail)
                .users(jwt.getSubject(), user.username)
                .success(Map.of("id", String.valueOf(user.id)));
        logger.info("{}", event.toJson(mapper));
        return new AccountResponse(ar.username, null);
    }

    @PUT
    @Path("/reset-password")
    @RolesAllowed("**")
    public AccountResponse resetPassword(AccountRequest ar) {
        if (ar == null || Strings.isNullOrEmpty(ar.username) || Strings.isNullOrEmpty(ar.password)) {
            var username = ar != null ? ar.username : "";
            var event =  event(Event.EventName.ResetPassword)
                    .users(ar.username, username)
                    .error("ActionNotAllowed", Map.of("username", Strings.nullToEmpty(username)));
            logger.warn("{}", event.toJson(mapper));
            throw new UnauthorizedException();
        }
        normalize(ar);

        var user = ud.getByEmail(ar.username).orElseThrow(() -> {
            var event =  event(Event.EventName.ResetPassword)
                    .users(ar.username, ar.username)
                    .error("AccountNotFound", Map.of("username", Strings.nullToEmpty(ar.username)));
            logger.warn("{}", event.toJson(mapper));
            return new UnauthorizedException();
        });

        if (user.status == User.Status.suspended) {
            var event =  event(Event.EventName.ResetPassword)
                    .users(user.username, user.username)
                    .error("AccountStatusSuspended", Map.of("username", Strings.nullToEmpty(ar.username),
                            "status", String.valueOf(user.status)));
            logger.warn("{}", event.toJson(mapper));
            throw new UnauthorizedException();
        }

        if (user.status == User.Status.pending) {
            user.status = User.Status.active;
        }

        user.password = auth.hashPassword(ar.password);
        user = ud.updatePasswordAndStatus(user);
        if (user == null) {
            var event =  event(Event.EventName.ResetPassword)
                    .users(user.username, user.username)
                    .error("AccountStatusUpdateError", Map.of("username", Strings.nullToEmpty(ar.username),
                            "status", String.valueOf(user.status)));
            logger.warn("{}", event.toJson(mapper));
            throw new UnauthorizedException();
        }
        var event = event(Event.EventName.ResetPassword)
                .users(user.username, user.username)
                .success(Map.of("id", String.valueOf(user.id)));
        logger.info("{}", event.toJson(mapper));
        return new AccountResponse(user.username, null);
    }

    @PUT
    @Path("/suspend")
    @RolesAllowed("**")
    public AccountResponse suspend(AccountRequest ar) {
        if (ar == null || Strings.isNullOrEmpty(ar.username)) {
            var username = ar != null ? ar.username : "";
            var event =  event(Event.EventName.SuspendAccount)
                    .users(jwt.getSubject(), username)
                    .error("ActionNotAllowed", Map.of("username", Strings.nullToEmpty(username)));
            logger.warn("{}", event.toJson(mapper));
            throw new UnauthorizedException();
        }
        normalize(ar);

        var user = ud.getByEmail(ar.username).orElseThrow(() -> {
            var event =  event(Event.EventName.SuspendAccount)
                    .users(jwt.getSubject(), null)
                    .error("AccountNotFound", Map.of("username", Strings.nullToEmpty(ar.username)));
            logger.warn("{}", event.toJson(mapper));
            return new UnauthorizedException();
        });

        if (user.status != User.Status.active) {
            var event =  event(Event.EventName.SuspendAccount)
                    .users(jwt.getSubject(), user.username)
                    .error("InvalidAccountStatus", Map.of("username", Strings.nullToEmpty(ar.username),
                            "status", String.valueOf(user.status)));
            logger.warn("{}", event.toJson(mapper));
            throw new UnauthorizedException();
        }

        user.password = auth.hashPassword(ar.password);
        var dbUser = ud.updatePasswordAndStatus(user);
        if (dbUser == null) {
            var event =  event(Event.EventName.SuspendAccount)
                    .users(jwt.getSubject(), user.username)
                    .error("AccountStatusUpdateError", Map.of("username", Strings.nullToEmpty(ar.username),
                            "status", String.valueOf(user.status)));
            logger.warn("{}", event.toJson(mapper));
            throw new UnauthorizedException();
        }
        var event = event(Event.EventName.SuspendAccount)
                .users(jwt.getSubject(), dbUser.username)
                .success(Map.of("id", String.valueOf(dbUser.id)));
        logger.info("{}", event.toJson(mapper));
        return new AccountResponse(dbUser.username, null);
    }

    protected void normalize(AccountRequest ar) {
        if (ar != null && !Strings.isNullOrEmpty(ar.username)) {
            ar.username = ar.username.toLowerCase();
        }
    }

    protected AccountResponse createOrUpdateAccount(AccountRequest ar) {
        if (ar == null || Strings.isNullOrEmpty(ar.username) || Strings.isNullOrEmpty(ar.password)) {
            var username = ar != null ? ar.username : "";
            var event =  event(Event.EventName.CreateAccount)
                    .users(jwt.getSubject(), username)
                    .error("ActionNotAllowed", Map.of("username", Strings.nullToEmpty(username)));
            logger.warn("{}", event.toJson(mapper));
            throw validator.invalidField("username", "invalid username");
        }

        normalize(ar);
        if (!validator.validateEmail(ar.username)) {
            var event =  event(Event.EventName.CreateAccount)
                    .users(jwt.getSubject(), ar.username)
                    .error("InvalidEmail", Map.of("username", Strings.nullToEmpty(ar.username)));
            logger.warn("{}", event.toJson(mapper));
            throw validator.invalidField("username", "invalid username");
        }

        //check if user exists
        ud.getByEmail(ar.username).ifPresent(u -> {
            var event =  event(Event.EventName.CreateAccount)
                    .users(jwt.getSubject(), u.username)
                    .error("AccountAlreadyExists", Map.of("username", Strings.nullToEmpty(ar.username)));
            logger.warn("{}", event.toJson(mapper));
            throw validator.invalidField("username", "invalid username");
        });

        User usr = new User(ar.username, auth.hashPassword(ar.password));
        usr.status = User.Status.pending;

        var dbUsr = ud.persist(usr);
        if (dbUsr == null) {
            var event =  event(Event.EventName.CreateAccount)
                    .users(jwt.getSubject(), usr.username)
                    .error("AccountPersistError", Map.of("username", Strings.nullToEmpty(ar.username)));
            logger.warn("{}", event.toJson(mapper));
            throw validator.invalidField("username", "invalid username");
        }

        var emailScheduled = ud.addPendingUserEmail(dbUsr, ar.lang, jwt.getSubject(),
                UserEmail.ACTION_VERIFY_EMAIL);
        if (!emailScheduled) {
            var event = event(Event.EventName.CreateAccount)
                    .users(jwt.getSubject(), dbUsr.username)
                    .error("EmailNotSent", Map.of("username", Strings.nullToEmpty(dbUsr.username)));
            logger.warn("{}", event.toJson(mapper));
            throw validator.createError(RequestValidator.ERROR_EMAIL_DISPATCH, "email",
                    "error sending email verification email");
        }
        var event = event(Event.EventName.CreateAccount)
                .users(jwt.getSubject(), dbUsr.username)
                .success(Map.of("id", String.valueOf(dbUsr.id)));
        logger.info("{}", event.toJson(mapper));
        return new AccountResponse(dbUsr.username, null);
    }

    public static class AccountRequest {
        public Long id;
        public String username;
        public String password;
        public String lang;
        public Integer status;

        @Override
        public String toString() {
            return "{" +
                    "\"id\":\"" + id + "\"" +
                    "\"username\":\"" + username + "\"" +
                    ",\"password\":\"" + password + "\"" +
                    ",\"lang\":\"" + lang + "\"" +
                    ",\"status\":\"" + status + "\"" +
                    "}";
        }
    }

    public static class AccountResponse {
        public String username;
        public String token;

        public AccountResponse() {
        }

        public AccountResponse(String username, String token) {
            this.username = username;
            this.token = token;
        }

        @Override
        public String toString() {
            return "{" +
                    "\"username\":\"" + username + "\"" +
                    ",\"token\":\"" + token + "\"" +
                    "}";
        }
    }
}
