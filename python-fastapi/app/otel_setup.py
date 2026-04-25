"""OpenTelemetry wiring — traces and metrics shipped via OTLP/gRPC on port 4317.

The provider and exporters are registered once at FastAPI startup. The OpenFeature
`TracingHook` attaches flag-evaluation spans as children of whatever span is
active, so the HTTP request span (created by `FastAPIInstrumentor`) ends up as
the parent of each flag evaluation span. The custom `FeatureFlagMetricsHook`
emits a counter against the `MeterProvider` configured here, which a
`PeriodicExportingMetricReader` flushes to the shared LGTM stack every 10s.
"""

from __future__ import annotations

from opentelemetry import metrics, trace
from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor


def configure_otel() -> None:
    """Register a global tracer + meter provider that ship to the LGTM stack."""
    resource = Resource.create({"service.name": "fun-with-flags-python-fastapi"})

    # Traces — batched span export to the OTel collector.
    provider = TracerProvider(resource=resource)
    provider.add_span_processor(
        BatchSpanProcessor(
            OTLPSpanExporter(endpoint="http://localhost:4317", insecure=True)
        )
    )
    trace.set_tracer_provider(provider)

    # Metrics — periodic push every 10s, plenty for a workshop demo.
    reader = PeriodicExportingMetricReader(
        OTLPMetricExporter(endpoint="http://localhost:4317", insecure=True),
        export_interval_millis=10_000,
    )
    metrics.set_meter_provider(MeterProvider(resource=resource, metric_readers=[reader]))
