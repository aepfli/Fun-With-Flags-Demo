package dev.openfeature.demo.quarkus;

import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.Map;

@Startup
@ApplicationScoped
public class OpenFeatureStartup {

    @PostConstruct
    public void initProvider() {
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();

        Map<String, Flag<?>> flags = new HashMap<>();
        flags.put("greetings", Flag.builder()
                .variant("hello", "Hello World!")
                .variant("goodbye", "Goodbye World!")
                .defaultVariant("hello")
                .build());

        api.setProviderAndWait(new InMemoryProvider(flags));
    }
}
