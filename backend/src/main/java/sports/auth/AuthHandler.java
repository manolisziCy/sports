package sports.auth;

import com.google.common.base.Strings;
import com.google.common.primitives.Longs;
import com.lambdaworks.crypto.SCryptUtil;
import io.smallrye.jwt.build.Jwt;
import org.eclipse.microprofile.jwt.JsonWebToken;
import sports.config.ConfigurationHandler;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

@ApplicationScoped
public class AuthHandler {
    /**
     * This is the SCrypt N factor, the work factor or hash iteration count.
     * It affects memory based on the formula:
     * memory = 128 * N * r
     */
    public static final int SCRYPT_N = 2 << 13;
    /**
     * This is the SCrypt r factor. Both CPU and memory requirements scale linearly with it:
     * it is used to convert functions with k-bits input/output to functions with (2*r*k)-bits inputs/outputs
     */
    public static final int SCRYPT_r = 8;
    /**
     * This is the SCrypt p factor.
     * It is the parallelization count, deciding how many times the overall process will be performed.
     * This is useful in case of multi-core systems, so as to increase the required CPUs to perform the work.
     * However, as our impl is sequential (and most others are) we can safely leave it to a low number,
     * as the memory and CPU requirements are already high
     */
    public static final int SCRYPT_p = 2;

    public static final String CLAIM_KEY_USER_ID = "userId";
    public static final String CLAIM_KEY_ACTION = "action";

    @Inject
    ConfigurationHandler config;

    PrivateKey jwtSignKey;

    @PostConstruct
    public void init() throws Exception {
        jwtSignKey = KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(
                        config.get("sports.jwt.privatekey", String.class, ""))));
    }

    public String generateJwt(long userId, String username) {
        return generateJwt(userId, username, null);
    }

    public String generateJwt(long userId, String username, String action) {
        Instant curr = Instant.now();
        Instant exp = curr.plusSeconds(config.get("smallrye.jwt.new-token.lifespan", Long.class, 3600L));
        final String iss = config.get("mp.jwt.verify.issuer", String.class, "");
        username = username.toLowerCase();

        return Jwt.claims()
                .subject(username)
                .upn(username)
                .issuer(iss)
                .issuedAt(curr.toEpochMilli() / 1000) //this takes seconds
                .expiresAt(exp.toEpochMilli() / 1000)
                .claim(CLAIM_KEY_USER_ID, Long.toString(userId))
                .claim(CLAIM_KEY_ACTION, Strings.nullToEmpty(action))
                .sign(jwtSignKey);
    }

    public String generateJwt(JsonWebToken jwt) {
        Long userId = AuthHandler.getUserId(jwt);
        if (userId == null) {
            return null;
        }

        return generateJwt(userId, jwt.getSubject());
    }

    public String hashPassword(String plain) {
        return SCryptUtil.scrypt(plain, SCRYPT_N, SCRYPT_r, SCRYPT_p);
    }

    public boolean checkPassword(String plain, String hash) {
        return SCryptUtil.check(plain, hash);
    }

    public static boolean hasInvalidSubject(JsonWebToken jwt) {
        return jwt == null || Strings.isNullOrEmpty(jwt.getSubject());
    }

    public static Long getUserId(JsonWebToken jwt) {
        if (jwt == null) {
            return null;
        }
        String id = jwt.getClaim(CLAIM_KEY_USER_ID);
        if (Strings.isNullOrEmpty(id)) {
            return null;
        }

        return Longs.tryParse(id);
    }
}