package sports.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.hibernate.types.json.JsonBinaryType;
import io.quarkiverse.hibernate.types.json.JsonTypes;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "events")
@TypeDef(name = JsonTypes.JSON_BIN, typeClass = JsonBinaryType.class)
public class Event extends PanacheEntityBase {
    private static final Logger logger = LoggerFactory.getLogger(Event.class);

    /**
     * A unique number for the event
     */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /**
     * The email of the account who performed the operation
     */
    @Column(name = "actor")
    public String actor;

    /**
     * The email of the account who affected by the operation
     */
    @Column(name = "recipient")
    public String recipient;

    /**
     * The time the operation took place in ISO 8601 format (e.g. 2019-11-14T00:52:00Z)
     */
    @Column(name = "event_time")
    public Instant eventTime;
    /**
     * The name of the event (DeleteUser, CreateUser, CreateForm etc)
     */
    @Column(name = "event_name")
    public String eventName;
    /**
     * Success, any other error specific code (e.g. NotFound, DifferentHash etc)
     */
    @Column(name = "error_code")
    public String errorCode;
    /**
     * Related request or response parameters.
     */
    @Type(type = JsonTypes.JSON_BIN)
    @Column(name = "parameters")
    public Map<String, Object> parameters;
    /**
     * The source ip of the request which generated the event
     */
    @Column(name = "ip")
    public String ip;
    /**
     * The user agent
     */
    @Column(name = "agent")
    public String agent;

    public Event() {
    }

    public Event(EventName eventName) {
        this.eventName = eventName.name();
        this.eventTime = Instant.now();
    }

    public static Event create(EventName eventName) {
        return new Event(eventName);
    }

    public Event client(String ip, String agent) {
        this.ip = ip;
        this.agent = agent;
        return this;
    }

    public Event users(String actor, String recipient) {
        this.actor = actor;
        this.recipient = recipient;
        return this;
    }

    public Event success(Map<String, Object> parameters) {
        return error("Success", parameters);
    }

    public Event error(String errorCode, Map<String, Object> parameters) {
        this.errorCode = errorCode;
        this.parameters = parameters;
        return this;
    }

    public String toJson(ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(this);
        } catch (Exception ex) {
            logger.warn("failed to produce json for {}", this, ex);
            return this.toString();
        }
    }

    @Override
    public String toString() {
        return "Event{" +
                ", actor='" + actor + '\'' +
                ", recipient='" + recipient + '\'' +
                ", eventTime='" + eventTime + '\'' +
                ", eventName='" + eventName + '\'' +
                ", errorCode='" + errorCode + '\'' +
                ", parameters=" + parameters +
                ", ip='" + ip + '\'' +
                '}';
    }

    public enum EventName {
        DeleteAccount, ListAccounts, GetAccount, Login, RefreshToken, VerifyEmail, SendVerificationEmail,
        ResetPassword, SendResetPasswordEmail, SuspendAccount, CreateAccount, UpdateAccount, CreateEvent,
        UpdateEvent, DeleteEvent, ListEvents, ProcessResetPasswordEmail, ProcessVerifyEmail, ProcessUserEmail;
    }
}
