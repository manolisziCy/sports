package gr.cytech.events;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

import java.util.TimeZone;

@QuarkusMain
public class App {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Quarkus.run(args);
    }
}
