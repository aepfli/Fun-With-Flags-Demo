package dev.openfeature.demo.java.demo;

import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class OpenFeatureConfig {

    @PostConstruct
    public void initProvider() {
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();

        Map<String, Object> variants = new HashMap<>();
        variants.put("hello", "Hello World!");
        variants.put("goodbye", "Goodbye World!");

        Flag<String> greetings = Flag.<String>builder()
                .variants(variants)
                .defaultVariant("hello")
                .build();

        Map<String, Flag<?>> flags = new HashMap<>();
        flags.put("greetings", greetings);

        api.setProviderAndWait(new InMemoryProvider(flags));
    }
}
