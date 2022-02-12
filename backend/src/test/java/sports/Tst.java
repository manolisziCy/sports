package sports;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;

public class Tst {

    /**
     * Generate a new RSA keypair.
     *
     * @param keySize - the size of the key
     * @return KeyPair
     * @throws NoSuchAlgorithmException on failure to load RSA key generator
     */
    public static KeyPair generateKeyPair(final int keySize) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(keySize);
        return keyPairGenerator.genKeyPair();
    }

    public static String[] generateDefaultKeyPair() throws Exception {
        KeyPair kp = generateKeyPair(2048);
        return new String[]{Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()),
                Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded())};
    }

    public static PrivateKey decodePrivateKey(final String pemEncoded) throws Exception {
        byte[] encodedBytes = toEncodedBytes(pemEncoded);

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encodedBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(keySpec);
    }

    public static PublicKey decodePublicKey(String pemEncoded) throws Exception {
        byte[] encodedBytes = toEncodedBytes(pemEncoded);

        X509EncodedKeySpec spec = new X509EncodedKeySpec(encodedBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    private static byte[] toEncodedBytes(final String pemEncoded) {
        final String normalizedPem = removeBeginEnd(pemEncoded);
        return Base64.getDecoder().decode(normalizedPem);
    }

    private static String removeBeginEnd(String pem) {
        pem = pem.replaceAll("-----BEGIN (.*)-----", "");
        pem = pem.replaceAll("-----END (.*)----", "");
        pem = pem.replaceAll("\r\n", "");
        pem = pem.replaceAll("\n", "");
        return pem.trim();
    }

    public static void main(String[] args) throws Exception {
//        String[] keys = generateDefaultKeyPair();
//        System.out.println("public: " + keys[0]);
//        System.out.println("private: " + keys[1]);
        //System.out.println(UUID.randomUUID());
//        Instant i = Instant.now();
//        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd");
//        System.out.println(dtf.format(i.atZone(ZoneOffset.UTC).toLocalDateTime().toLocalDate()));
        Duration d = Duration.parse("PT6H");
        System.out.println(d);
    }
}
