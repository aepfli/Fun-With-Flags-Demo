package dev.openfeature.demo.java.demo;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Step 6: Boots an OpenTelemetry SDK (OTLP gRPC exporter -> LGTM stack at :4317)
 * using the SDK autoconfigure module. The resulting {@link OpenTelemetry} is
 * exposed as a bean so the OpenFeature OTel hooks (TracesHook + MetricsHook)
 * can pick it up. Both a {@code SdkTracerProvider} and a {@code SdkMeterProvider}
 * are configured automatically by the autoconfigure module based on
 * {@code otel.traces.exporter} / {@code otel.metrics.exporter} properties.
 */
@Configuration
public class OpenTelemetryConfig {

    private AutoConfiguredOpenTelemetrySdk autoConfigured;

    @Bean
    public OpenTelemetry openTelemetry(
            @Value("${otel.service.name:fun-with-flags-java-spring}") String serviceName,
            @Value("${otel.exporter.otlp.endpoint:http://localhost:4317}") String otlpEndpoint,
            @Value("${otel.exporter.otlp.protocol:grpc}") String otlpProtocol,
            @Value("${otel.traces.exporter:otlp}") String tracesExporter,
            @Value("${otel.metrics.exporter:otlp}") String metricsExporter,
            @Value("${otel.logs.exporter:none}") String logsExporter,
            @Value("${otel.metric.export.interval:10000}") String metricExportInterval) {
        // Expose configured values via system properties so the SDK autoconfigure
        // module picks them up regardless of how the app was launched.
        System.setProperty("otel.service.name", serviceName);
        System.setProperty("otel.exporter.otlp.endpoint", otlpEndpoint);
        System.setProperty("otel.exporter.otlp.protocol", otlpProtocol);
        System.setProperty("otel.traces.exporter", tracesExporter);
        System.setProperty("otel.metrics.exporter", metricsExporter);
        System.setProperty("otel.logs.exporter", logsExporter);
        System.setProperty("otel.metric.export.interval", metricExportInterval);

        // AutoConfiguredOpenTelemetrySdk wires up both SdkTracerProvider and
        // SdkMeterProvider (with an OtlpGrpcMetricExporter when otel.metrics.exporter=otlp
        // and otel.exporter.otlp.protocol=grpc). The returned OpenTelemetry bean
        // therefore exposes both getTracerProvider() and getMeterProvider().
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
