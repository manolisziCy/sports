package sports.providers;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import java.util.concurrent.TimeUnit;

public class DependencyProducer {
    @Produces
    @Singleton
    public Client produceHttpClient() {
        ResteasyClientBuilder builder = (ResteasyClientBuilder) ResteasyClientBuilder.newBuilder();
        return builder.connectionPoolSize(100)
                .connectTimeout(2, TimeUnit.MINUTES)
                .readTimeout(3, TimeUnit.MINUTES)
                .build();
    }
}
