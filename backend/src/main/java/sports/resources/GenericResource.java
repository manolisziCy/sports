package sports.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import io.vertx.core.http.HttpServerRequest;
import org.eclipse.microprofile.jwt.JsonWebToken;
import sports.auth.AuthHandler;
import sports.core.Event;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequestScoped
public class GenericResource {
    @Context HttpServerRequest request;
    @HeaderParam("X-Forwarded-For") String forwardedFor;
    @HeaderParam("user-agent") String userAgent;

    @Inject AuthHandler auth;
    @Inject JsonWebToken jwt;
    @Inject ObjectMapper mapper;
    @Inject RequestValidator validator;

    protected Event event(Event.EventName name) {
        return Event.create(name).client(remoteIp(), userAgent);
    }

    protected String remoteIp() {
        return !Strings.isNullOrEmpty(forwardedFor) ? forwardedFor :
                request != null && request.remoteAddress() != null ? request.remoteAddress().host() : null;
    }

    public static Map<String, String> extractFilters(UriInfo uriInfo) {
        Map<String, String> filters = new HashMap<>();
        for (var prm : uriInfo.getQueryParameters().keySet()) {
            if (prm.startsWith("filter_")) {
                filters.put(prm.substring("filter_".length()), uriInfo.getQueryParameters().getFirst(prm));
            }
        }
        return filters;
    }

    public static String join(String delimiter, String... str) {
        return Stream.of(str).filter(s -> !Strings.isNullOrEmpty(s)).collect(Collectors.joining(delimiter));
    }
}
