package sports.config;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigurationHandlerTest {
    public static final Map<String, String> CONFIG_PAUSE = Arrays.stream(ConfigurationHandler.Prop.values())
            .filter(p -> p.key.contains("pause")).collect(Collectors.toMap(p -> p.key, p -> "true"));

    public static final Map<String, String> CONFIG_UNPAUSE = CONFIG_PAUSE.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> "false"));
}