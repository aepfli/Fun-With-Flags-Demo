package dev.openfeature.demo.java.demo;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Step 6: Boots an OpenTelemetry SDK (OTLP gRPC exporter -> Jaeger at :4317)
 * using the SDK autoconfigure module. The resulting {@link OpenTelemetry} is
 * exposed as a bean so the OpenFeature OTel hook can pick it up.
 */
@Configuration
public class OpenTelemetryConfig {

    private AutoConfiguredOpenTelemetrySdk autoConfigured;

    @Bean
    public OpenTelemetry openTelemetry(
            @Value("${otel.service.name:fun-with-flags-java-spring}") String serviceName,
            @Value("${otel.exporter.otlp.endpoint:http://localhost:4317}") String otlpEndpoint) {
        // Expose configured values via system properties so the SDK autoconfigure
        // module picks them up regardless of how the app was launched.
        System.setProperty("otel.service.name", serviceName);
        System.setProperty("otel.exporter.otlp.endpoint", otlpEndpoint);
        System.setProperty("otel.traces.exporter", System.getProperty("otel.traces.exporter", "otlp"));
        System.setProperty("otel.metrics.exporter", System.getProperty("otel.metrics.exporter", "none"));
        System.setProperty("otel.logs.exporter", System.getProperty("otel.logs.exporter", "none"));

        autoConfigured = AutoConfiguredOpenTelemetrySdk.builder()
                .setResultAsGlobal()
                .build();
        return autoConfigured.getOpenTelemetrySdk();
    }

    @PreDestroy
    public void shutdown() {
        if (autoConfigured != null) {
            autoConfigured.getOpenTelemetrySdk().close();
        }
    }
}
