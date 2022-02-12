package sports.daos;

import sports.IntegrationTest;
import sports.config.ConfigurationHandlerTest;
import sports.core.EventsTest;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class EventDaoTest extends IntegrationTest {
    @BeforeEach
    public void beforeEach() {
        cdao.createOrUpdate(ConfigurationHandlerTest.CONFIG_PAUSE);
    }

    @AfterEach
    public void afterAll() {
        cdao.createOrUpdate(ConfigurationHandlerTest.CONFIG_UNPAUSE);
    }

    @Test
    public void testPersist() {
        var event = EventsTest.generateSampleEvent();
        event = dao.persist(event);
        assertThat(event).isNotNull();
        assertThat(event.id).isNotNull();

        //get by id
        var dbEvent = dao.getById(event.id).orElseThrow();
        Assertions.assertThat(dbEvent).usingRecursiveComparison().ignoringFieldsMatchingRegexes("\\$\\$.*").ignoringFields("eventTime").isEqualTo(event);

        Assertions.assertThat(dbEvent.eventTime.truncatedTo(ChronoUnit.MINUTES))
                .isNotNull().isEqualTo(event.eventTime.truncatedTo(ChronoUnit.MINUTES));
    }

    @Test
    public void testDelete(){
        var event = EventsTest.generateSampleEvent();
        event = dao.persist(event);
        assertThat(event.id).isNotNull();
        dao.delete(event);
        var byid = dao.getById(event.id);
        Assertions.assertThat(byid).isEmpty();
    }
}
