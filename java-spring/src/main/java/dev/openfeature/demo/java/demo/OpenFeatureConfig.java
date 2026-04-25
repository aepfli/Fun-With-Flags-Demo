package dev.openfeature.demo.java.demo;

import dev.openfeature.contrib.hooks.otel.MetricsHook;
import dev.openfeature.contrib.hooks.otel.TracesHook;
import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.SpringVersion;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.HashMap;

@Configuration
public class OpenFeatureConfig implements WebMvcConfigurer {

    // Depend on the OpenTelemetry bean so the global SDK is initialized
    // before the first flag evaluation happens inside initProvider().
    private final io.opentelemetry.api.OpenTelemetry openTelemetry;

    public OpenFeatureConfig(io.opentelemetry.api.OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @PostConstruct
    public void initProvider() {
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        FlagdOptions flagdOptions = FlagdOptions.builder()
                .resolverType(Config.Resolver.RPC)
                .offlineFlagSourcePath("./flags.json")
                .build();

        api.setProviderAndWait(new FlagdProvider(flagdOptions));

        HashMap<String, Value> attributes = new HashMap<>();
        attributes.put("springVersion", new Value(SpringVersion.getVersion()));
        ImmutableContext evaluationContext = new ImmutableContext(attributes);
        api.setEvaluationContext(evaluationContext);

        api.addHooks(new CustomHook());
        api.addHooks(new TracesHook());
        api.addHooks(new MetricsHook(openTelemetry));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LanguageInterceptor());
    }
}
