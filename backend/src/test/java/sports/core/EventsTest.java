package sports.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class EventsTest {
    public static final AtomicInteger UniqueNumber = new AtomicInteger();

    @Test
    public void serialization() throws Exception {
        Event event = generateSampleEvent();
        ObjectMapper om = new ObjectMapper().findAndRegisterModules().configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        String json = om.writeValueAsString(event);

        var form = om.readValue(json, Event.class);

        assertThat(form).usingRecursiveComparison().isEqualTo(event);
    }

    public static Event generateSampleEvent() {
        var event = new Event();
        event.eventName = UUID.randomUUID().toString();
        event.eventTime = Instant.now();
        event.errorCode = "error" + UniqueNumber.get();
        event.actor = "actor" + UniqueNumber.get() + "@cytech.gr";
        event.recipient = "recipient" + UniqueNumber.get() + "@cytech.gr";
        event.parameters = new HashMap<>();
        event.parameters.put("name", event.eventName);

        return event;
    }
}
