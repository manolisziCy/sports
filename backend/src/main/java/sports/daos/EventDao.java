package sports.daos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sports.core.Event;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.HashMap;
import java.util.Optional;

@ApplicationScoped
public class EventDao {
    private static final Logger logger = LoggerFactory.getLogger(EventDao.class);

    @Inject EntityManager em;

    @Transactional
    public Event persist(Event event) {
        try {
            if (event.parameters == null) {
                event.parameters = new HashMap<>();
            }
            if (event.id == null) {
                event.persist();
            } else {
                Event dbEvent = Event.findById(event.id);
                em.detach(dbEvent);
                event = em.merge(event);
            }
            return event;
        } catch (Exception e) {
            logger.error("error inserting event {}", event, e);
            return null;
        }
    }

    @Transactional(value = Transactional.TxType.REQUIRED)
    public Event delete(Event event) {
        try {
            if (event.isPersistent()) {
                event.delete();
            } else {
                Event.deleteById(event.id);
            }
            return event;
        } catch (Exception e) {
            logger.warn("error deleting event: {}", event, e);
            return null;
        }
    }

    @Transactional
    public Optional<Event> getById(long id) {
        try {
            return Event.findByIdOptional(id);
        } catch (Exception e) {
            logger.warn("error finding event by id: {}", id, e);
            return Optional.empty();
        }
    }
}