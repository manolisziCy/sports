package gr.cytech.events.config;

import gr.cytech.events.daos.ConfigurationDao;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class DatabaseConfigSource implements ConfigSource {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfigSource.class);
    private static final int ORDINAL = 600;

    private static Map<String, String> config;

    @Inject ConfigurationDao dao;
    @Inject Event<ReconfigureEvent> reconfigureEventEvent;

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }

    public void onStart(@Observes StartupEvent ev, ConfigurationDao dao) {
        logger.info("The application is starting... Extracting db configuration");
        config = new HashMap<>(dao.getAllAsMap());
        logger.info("The application is starting... DB configuration injected");
    }

    @Override
    public Map<String, String> getProperties() {
        return getConfig();
    }

    @Override
    public Set<String> getPropertyNames() {
        return config.keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return getConfig().get(propertyName);
    }

    @Override
    public String getName() {
        return DatabaseConfigSource.class.getName();
    }

    @Scheduled(every = "30s")
    public void configReload() {
        logger.debug("reloading config");
        var map = loadConfig();
        var old = config;
        config = map;
        if (!map.equals(old)) {
            logger.info("reloaded changed configuration: {}", map);
            reconfigureEventEvent.fire(new ReconfigureEvent(old));
        }
    }

    protected Map<String, String> getConfig() {
        if (config == null) {
            if (dao == null) {
                synchronized (DatabaseConfigSource.class) {
                    if (config == null) {
                        config = manuallyGetConfig();
                        return config;
                    }
                }
            } else {
                config = dao.getAllAsMap();
            }
        }
        return config;
    }

    protected Map<String, String> loadConfig() {
        return dao.getAllAsMap();
    }

    protected Map<String, String> manuallyGetConfig() {
        logger.warn("no dao yet, manually retrieving config");
        try {
            var env = System.getenv();
            if (env.get("DB_HOST") == null) {
                logger.warn("no DB_HOST env var to manually get config on startup. Returning empty db config");
                return Map.of();
            }
            var pgds = new PGSimpleDataSource();
            pgds.setUser(env.getOrDefault("DB_USER", "sports"));
            pgds.setPassword(env.getOrDefault("DB_PASS", "sports"));
            pgds.setServerNames(new String[]{env.getOrDefault("DB_HOST", "localhost")});
            int port = Integer.parseInt(env.getOrDefault("DB_PORT", "5432"));
            pgds.setPortNumbers(new int[]{port});
            pgds.setDatabaseName(env.getOrDefault("DB_NAME", "sports"));

            try (var c = pgds.getConnection();
                    var ps = c.prepareStatement("SELECT key,value FROM configuration");
                    var rs = ps.executeQuery();
            ) {
                Map<String, String> results = new HashMap<>();
                while (rs.next()) {
                    results.put(rs.getString("key"), rs.getString("value"));
                }
                return results;
            }
        } catch (Exception e) {
            logger.warn("error manually getting config values", e);
            return Map.of();
        }
    }
}
