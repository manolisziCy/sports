package sports.core;

public class UserEmail {
    public static final String ACTION_VERIFY_EMAIL = "VerifyEmail";
    public static final String ACTION_RESET_PASSWORD = "ResetPassword";

    public User user;
    public String lang;
    public Integer retries;
    public String actor;
    public String action;

    public UserEmail() {
    }

    @Override
    public String toString() {
        return "UserEmail{" +
                "user=" + user +
                ", lang='" + lang + '\'' +
                ", retries=" + retries +
                ", actor='" + actor + '\'' +
                ", action='" + action + '\'' +
                '}';
    }
}
