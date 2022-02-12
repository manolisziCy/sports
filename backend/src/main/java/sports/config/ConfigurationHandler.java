package sports.config;

import com.google.common.base.Strings;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.enterprise.context.RequestScoped;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

@RequestScoped
public class ConfigurationHandler {

    public enum Prop {
        QUARKUS_SES_MOCK("quarkus.ses.mock"),
        MAIL_TYPE("sports.mail.type"),

        API_BATCH_MAX_LIMIT("sports.api.batch.max.limit"),
        API_BATCH_TIMEOUT("sports.api.batch.timeout"),

        USER_PROCESSOR_EMAIL_PAUSE("user.processor.email.pause"),
        USER_PROCESSOR_EMAIL_THREADS("user.processor.email.threads"),
        USER_PROCESSOR_EMAIL_SLEEP("user.processor.email.sleep"),
        USER_PROCESSOR_EMAIL_TPS("user.processor.email.tps"),
        USER_PROCESSOR_EMAIL_LIMIT("user.processor.email.limit"),
        USER_PROCESSOR_EMAIL_RETRY_INTERVAL("user.processor.email.retry.interval"),

        FORM_RENDER_URL("sports.form.render.url"),

        EMAIL_VERIFY_SENDER("sports.email.verify.sender"),
        EMAIL_VERIFY_URL("sports.email.verify.url"),

        EMAIL_RESET_PASSWORD_SENDER("sports.email.reset.password.sender"),
        EMAIL_RESET_PASSWORD_URL("sports.email.reset.password.url"),

        USER_EMAIL_RETRIES("user.email.max.retries"),
        USER_MAX_THROTTLING("user.max.throttling"),
        ;

        public final String key;

        Prop(String val) {
            this.key = val;
        }

        public <T> Optional<T> get(Class<T> type) {
            return ConfigProvider.getConfig().getOptionalValue(this.key, type);
        }
    }

    public ConfigurationHandler() {
        //for cdi
    }

    public Integer getUserMaxEmailTries() {
        return get(Prop.USER_EMAIL_RETRIES,Integer.class,1);
    }

    public String getMaxThrottling() {
        return get(Prop.USER_MAX_THROTTLING.key,String.class,"1 minute");
    }

    public String getProcessorUserEmailRetryInterval(boolean isTest) {
        return get(Prop.USER_PROCESSOR_EMAIL_RETRY_INTERVAL.key, String.class, "1 minute");
    }

    public String getEmailVerifySender() {
        return get(Prop.EMAIL_VERIFY_SENDER.key, String.class, "");
    }

    public String getEmailVerifyUrl() {
        return get(Prop.EMAIL_VERIFY_URL.key, String.class, "");
    }

    public String getEmailResetPasswordSender() {
        return get(Prop.EMAIL_RESET_PASSWORD_SENDER.key, String.class, "");
    }

    public String getEmailResetPasswordUrl() {
        return get(Prop.EMAIL_RESET_PASSWORD_URL.key, String.class, "");
    }

    public int getEmailUserLimit() {
        return get(Prop.USER_PROCESSOR_EMAIL_LIMIT.key, Integer.class, 25);
    }

    public boolean isQuarkusSesMocked() {
        return get(Prop.QUARKUS_SES_MOCK.key, Boolean.class, false);
    }

    public String getMailType() {
        return get(Prop.MAIL_TYPE, String.class, "");
    }

    public boolean isMailTypeSmtp() {
        return "smtp".equalsIgnoreCase(getMailType());
    }

    public  <T> T get(Prop key, Class<T> type, T defaultValue) {
        return get(key.key, type, defaultValue);
    }

    public  <T> T get(String key, Class<T> type, T defaultValue) {
        var val = ConfigProvider.getConfig().getOptionalValue(key, type);
        return val.orElse(defaultValue);
    }

    public String getString(String key, String lang) {
        lang = Strings.isNullOrEmpty(lang) ? "en" : lang.toLowerCase();
        var cp = ConfigProvider.getConfig();
        if (!key.endsWith(".")) {
            key = key + ".";
        }
        final String l = lang;
        final String k = key;
        return cp.getOptionalValue(k + l, String.class).orElseGet(() -> getNoMatchValue(k, l));
    }

    protected String getNoMatchValue(String key, String lang) {
        return "en".equals(lang) ? "" : getString(key, "en");
    }

    @Override
    public String toString() {
        return Arrays.stream(Prop.values())
                .collect(Collectors.toMap(p -> p.key, p -> get(p.key, String.class, "")))
                .toString();
    }
}
