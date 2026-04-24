"""OpenTelemetry wiring — shared Jaeger via OTLP/gRPC on port 4317.

The provider and exporter are registered once at FastAPI startup. The OpenFeature
`TracingHook` attaches flag-evaluation spans as children of whatever span is
active, so the HTTP request span (created by `FastAPIInstrumentor`) ends up as
the parent of each flag evaluation span.
"""

from __future__ import annotations

from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor


def configure_otel() -> None:
    """Register a global tracer provider that ships spans to the observability stack."""
    resource = Resource.create({"service.name": "fun-with-flags-python-fastapi"})
    provider = TracerProvider(resource=resource)
    provider.add_span_processor(
        BatchSpanProcessor(
            OTLPSpanExporter(endpoint="http://localhost:4317", insecure=True)
        )
    )
    trace.set_tracer_provider(provider)
