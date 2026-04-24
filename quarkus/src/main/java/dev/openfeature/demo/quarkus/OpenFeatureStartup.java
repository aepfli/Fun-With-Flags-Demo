package dev.openfeature.demo.quarkus;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ThreadLocalTransactionContextPropagator;
import dev.openfeature.sdk.Value;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;

@Startup
@ApplicationScoped
public class OpenFeatureStartup {

    @PostConstruct
    public void initProvider() {
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        FlagdOptions flagdOptions = FlagdOptions.builder()
                .resolverType(Config.Resolver.FILE)
                .offlineFlagSourcePath("./flags.json")
                .build();

        api.setProviderAndWait(new FlagdProvider(flagdOptions));

        HashMap<String, Value> attributes = new HashMap<>();
        // Quarkus does not expose its version via a clean runtime API the way
        // Spring has SpringVersion.getVersion(). The build injects the platform
        // version as a system property, and we fall back to "3.0.0" so the
        // targeting rule keeps working in tests that do not set the property.
        String version = System.getProperty("quarkus.platform.version", "3.0.0");
        attributes.put("quarkusVersion", new Value(version));
        ImmutableContext evaluationContext = new ImmutableContext(attributes);
        api.setEvaluationContext(evaluationContext);

        api.setTransactionContextPropagator(new ThreadLocalTransactionContextPropagator());
        api.addHooks(new CustomHook());
    }
}
