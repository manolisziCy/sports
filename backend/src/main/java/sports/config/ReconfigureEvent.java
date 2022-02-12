package sports.config;

import java.util.Map;

public class ReconfigureEvent {
    public final Map<String, String>  oldConfig;

    public ReconfigureEvent(Map<String, String> oldConfig) {
        this.oldConfig = oldConfig;
    }
}
