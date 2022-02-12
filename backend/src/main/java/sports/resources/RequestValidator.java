package sports.resources;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.validation.Validator;
import javax.validation.constraints.Email;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;

@RequestScoped
public class RequestValidator {
    public static final int ERROR_INVALID_FIELD = 1;
    public static final int ERROR_INVALID_USER_STATUS = 2;
    public static final int ERROR_THROTTLING = 3;
    public static final int ERROR_EMAIL_DISPATCH = 4;

    @Inject Validator validator;

    public boolean validateEmail(String email) {
        return validator.validate(new EmailValidation(email)).isEmpty();
    }

    public ValidationError invalidField(String field, String message) {
        return createError(ERROR_INVALID_FIELD, field, message);
    }

    public ValidationError createError(int error, String field, String message) {
        return new ValidationError(error, field, message);
    }

    public static class ValidationError extends BadRequestException {
        public final int error;
        public final String field;
        public final String message;

        @JsonCreator
        public ValidationError(
                @JsonProperty("error") int error,
                @JsonProperty("field") String field,
                @JsonProperty("message") String message) {
            super(Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":" + error + ",\"field\":\"" + field + "\",\"message\":\"" + message + "\"}")
                    .build());
            this.error = error;
            this.field = field;
            this.message = message;
        }
    }

    public static class EmailValidation {
        @Email(regexp = ".+@.+\\..+")
        public String email;

        public EmailValidation(String email) {
            this.email = email;
        }
    }
}
