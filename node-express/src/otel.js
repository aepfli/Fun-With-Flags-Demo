import { NodeSDK } from '@opentelemetry/sdk-node';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-grpc';
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-grpc';
import { PeriodicExportingMetricReader } from '@opentelemetry/sdk-metrics';
import { getNodeAutoInstrumentations } from '@opentelemetry/auto-instrumentations-node';
import { resourceFromAttributes } from '@opentelemetry/resources';
import { ATTR_SERVICE_NAME } from '@opentelemetry/semantic-conventions';

// Boot the OpenTelemetry SDK before anything else imports. Auto-instrumentation
// hooks Express/HTTP at require-time, so the order of imports in src/index.js
// matters: './otel.js' must be the very first import.
const sdk = new NodeSDK({
  resource: resourceFromAttributes({
    [ATTR_SERVICE_NAME]: 'fun-with-flags-node-express',
  }),
  traceExporter: new OTLPTraceExporter({ url: 'http://localhost:4317' }),
  metricReader: new PeriodicExportingMetricReader({
    exporter: new OTLPMetricExporter({ url: 'http://localhost:4317' }),
    exportIntervalMillis: 10_000,
  }),
  instrumentations: [getNodeAutoInstrumentations()],
});

sdk.start();

// Flush pending spans and metrics on SIGTERM so the LGTM stack shows the last request.
process.on('SIGTERM', () => {
  sdk
    .shutdown()
    .catch(() => {})
    .finally(() => process.exit(0));
});
