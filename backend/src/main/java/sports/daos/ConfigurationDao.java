package sports.daos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Tuple;
import javax.transaction.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ConfigurationDao {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationDao.class);
    @Inject EntityManager em;

    @Transactional(value = Transactional.TxType.REQUIRED)
    public Map<String, String> getAllAsMap() {
        Map<String, String> map = new HashMap<>();
        try {
            List<Tuple> tuples = em.createNativeQuery("SELECT key, value FROM configuration", Tuple.class)
                    .getResultList();

            for (Tuple t : tuples) {
                map.put(t.get("key", String.class), t.get("value", String.class));
            }

        } catch (Exception e) {
            logger.warn("error getting configuration tuples", e);
        }

        return map;
    }

    @Transactional(value = Transactional.TxType.REQUIRED)
    public boolean createOrUpdate(Map<String, String> map) {
        try {
            for (Map.Entry<String, String> e : map.entrySet()) {
                em.createNativeQuery(
                        "INSERT INTO configuration (key, value) values (:key, :value) " +
                                "ON CONFLICT (key) DO UPDATE set value=:value")
                        .setParameter("key", e.getKey())
                        .setParameter("value", e.getValue())
                        .executeUpdate();
            }
            return true;
        } catch (Exception e) {
            logger.warn("error updating configuration with: {}", map, e);
            return false;
        }
    }
}
