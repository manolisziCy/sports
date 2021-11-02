package gr.cytech.events.resources;

import gr.cytech.events.IntegrationTest;
import gr.cytech.events.config.ConfigurationHandler;
import gr.cytech.events.core.EventsTest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class EventResourceTest extends IntegrationTest {

    @BeforeEach
    public void setup() {
        resumeProcessor(ConfigurationHandler.Prop.USER_PROCESSOR_EMAIL_PAUSE.key);
    }

    @Test
    public void createEvent() {
        var event = EventsTest.generateSampleEvent();
        var received = requestCreateEvent(event);
        assertThat(received.id).isNotNull();
    }

    @Test
    public void createEvent_InvalidAuth() {
        var event = EventsTest.generateSampleEvent();
        givenJsonRequest().body(event).post("/api/event").then().statusCode(401);
    }

    @Test
    public void deleteEvent() throws Exception{
        var resp = createUser("test@test.com");
        var event = EventsTest.generateSampleEvent();
        event = dao.persist(event);
        assertThat(event.id).isNotNull();
        givenJsonRequest().header(HttpHeaders.AUTHORIZATION, "Bearer " + resp.token).delete("/api/event/"+event.id).then().statusCode(400);
        event.recipient = resp.username;
        dao.persist(event);
        givenJsonRequest().header(HttpHeaders.AUTHORIZATION, "Bearer " + resp.token).delete("/api/event/"+event.id).then().statusCode(200);
        var byid = dao.getById(event.id);
        assertThat(byid).isEmpty();
    }
}
