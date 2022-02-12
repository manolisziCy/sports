package sports.core;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Arrays;

public class User {
    public enum Status {
        pending(0), active(1), suspended(2);
        public final int val;

        Status(int val) {
            this.val = val;
        }

        public static Status from(int val) {
            return Arrays.stream(values()).filter(s -> s.val == val).findFirst().orElse(null);
        }
    }

    @JsonProperty("id")
    public long id;

    @JsonProperty("username")
    public String username;

    @JsonProperty("password")
    public String password;

    @JsonProperty("status")
    public Status status;

    public Instant createdAt;
    public Instant lastLogin;

    public User() {
    }

    public User(String username, String password) {
        this.id = 0;
        this.username = username;
        this.password = password;
        this.status = Status.pending;
    }

    @Override
    public String toString() {
        return "{" +
                "\"id\":\"" + id + "\"" +
                ",\"username\":\"" + username + "\"" +
                ",\"password\":\"" + password + "\"" +
                ",\"status\":\"" + status + "\"" +
                "}";
    }
}
