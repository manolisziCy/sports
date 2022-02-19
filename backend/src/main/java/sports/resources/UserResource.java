package sports.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import io.quarkus.security.UnauthorizedException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sports.auth.AuthHandler;
import sports.config.ConfigurationHandler;
import sports.core.User;
import sports.daos.UserDao;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
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
            logger.warn("|delete-my-account|error|action-not-allowed|{}", jwt.getSubject());
            throw new UnauthorizedException();
        }
        var user = ud.getByEmail(jwt.getSubject()).orElseThrow(() -> {
            logger.warn("|delete-my-account|error|action-not-allowed|get-by-email|{}", jwt.getSubject());
            return new UnauthorizedException();
        });
        ud.delete(user);
        logger.info("|delete-my-account|success|{}", jwt.getSubject());
        return new AccountResponse(user.username, null);
    }

    @POST
    @Path("/login")
    @PermitAll
    public AccountResponse login(AccountRequest lr) throws JsonProcessingException {
        if (lr == null || Strings.isNullOrEmpty(lr.username) || Strings.isNullOrEmpty(lr.password)) {
            var username = lr != null ? lr.username : null;
            logger.warn("|login|error|action-not-allowed|{}", username);
            throw new UnauthorizedException();
        }
        normalize(lr);
        var user = ud.getByEmail(lr.username).orElseThrow(() -> {
            logger.warn("|login|error|action-not-allowed|get-by-email|{}", jwt.getSubject());
            return new UnauthorizedException();
        });

        if (user.status != User.Status.active) {
            logger.warn("|login|error|inactive-account|{}", user.username);
            if (user.status == User.Status.pending) {
                throw validator.createError(RequestValidator.ERROR_INVALID_USER_STATUS, "status", "status is pending");
            }
            throw new UnauthorizedException();
        }

        if (!auth.checkPassword(lr.password, user.password)) {
            logger.warn("|login|error|invalid-password|{}",user.username);
            throw new UnauthorizedException();
        }

        ud.updateLastLogin(user);

        logger.info("|login|success|{}", user.username);
        return new AccountResponse(user.username, auth.generateJwt(user.id, user.username));
    }

    @POST
    @Path("/refresh")
    @RolesAllowed("**")
    public AccountResponse refresh() {
        if (AuthHandler.hasInvalidSubject(jwt)) {
            logger.warn("|refresh|error|action-not-allowed|{}", jwt.getSubject());
            throw new UnauthorizedException();
        }

        var resp = new AccountResponse();
        resp.username = jwt.getSubject();
        resp.token = auth.generateJwt(jwt);
        return resp;
    }

    @PUT
    @Path("/reset-password")
    @RolesAllowed("**")
    public AccountResponse resetPassword(AccountRequest ar) {
        if (ar == null || Strings.isNullOrEmpty(ar.username) || Strings.isNullOrEmpty(ar.password)) {
            var username = ar != null ? ar.username : "";
            logger.warn("|reset-password|error|action-not-allowed|{}", username);
            throw new UnauthorizedException();
        }
        normalize(ar);

        var user = ud.getByEmail(ar.username).orElseThrow(() -> {
            logger.warn("|reset-password|error|account-not-found|{}", ar.username);
            return new UnauthorizedException();
        });

        if (user.status == User.Status.suspended) {
            logger.warn("|reset-password|error|account-status-suspended|{}", user.username);
            throw new UnauthorizedException();
        }

        if (user.status == User.Status.pending) {
            user.status = User.Status.active;
        }

        user.password = auth.hashPassword(ar.password);
        user = ud.updatePasswordAndStatus(user);
        if (user == null) {
            logger.warn("|reset-password|error|account-status-update-error|{}", user.username);
            throw new UnauthorizedException();
        }
        logger.info("|reset-password|success|{}", user.username);
        return new AccountResponse(user.username, null);
    }

    @PUT
    @Path("/suspend")
    @RolesAllowed("**")
    public AccountResponse suspend(AccountRequest ar) {
        if (ar == null || Strings.isNullOrEmpty(ar.username)) {
            var username = ar != null ? ar.username : "";
            logger.warn("|suspend|error|action-not-allowed|{}", username);
            throw new UnauthorizedException();
        }
        normalize(ar);

        var user = ud.getByEmail(ar.username).orElseThrow(() -> {
            logger.warn("|suspend|error|account-not-found|{}", jwt.getSubject());
            return new UnauthorizedException();
        });

        if (user.status != User.Status.active) {
            logger.warn("|suspend|error|invalid-account-status|{}", jwt.getSubject());
            throw new UnauthorizedException();
        }

        user.password = auth.hashPassword(ar.password);
        var dbUser = ud.updatePasswordAndStatus(user);
        if (dbUser == null) {
            logger.warn("|suspend|error|account-status-update-error|{}", user.username);
            throw new UnauthorizedException();
        }
        logger.info("|suspend|success|{}", user.username);
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
            logger.warn("|create-or-update-account|error|action-not-allowed|{}", username);
            throw validator.invalidField("username", "invalid username");
        }

        normalize(ar);
        if (!validator.validateEmail(ar.username)) {
            logger.warn("|create-or-update-account|error|invalid-email|{}", ar.username);
            throw validator.invalidField("username", "invalid username");
        }

        //check if user exists
        ud.getByEmail(ar.username).ifPresent(u -> {
            logger.warn("|create-or-update-account|error|account-already-exists|{}", ar.username);
            throw validator.invalidField("username", "invalid username");
        });

        User usr = new User(ar.username, auth.hashPassword(ar.password));
        usr.status = User.Status.pending;

        var dbUsr = ud.persist(usr);
        if (dbUsr == null) {
            logger.warn("|create-or-update-account|error|account-persist-error|{}", usr.username);
            throw validator.invalidField("username", "invalid username");
        }

        logger.warn("|create-or-update-account|success|{}", usr.username);
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
