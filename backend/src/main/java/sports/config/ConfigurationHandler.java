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
        API_BATCH_MAX_LIMIT("sports.api.batch.max.limit"),
        USER_PROCESSOR_EMAIL_PAUSE("user.processor.email.pause"),
        FORM_RENDER_URL("sports.form.render.url"),
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

    public String getMaxThrottling() {
        return get(Prop.USER_MAX_THROTTLING.key,String.class,"1 minute");
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
