// Package otelsetup configures the OpenTelemetry tracer provider used by the
// go-chi demo. It wires an OTLP/gRPC exporter pointed at the shared Jaeger in
// ../observability so flag evaluation spans land in the UI out of the box.
package otelsetup

import (
	"context"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.26.0"
)

// Init builds and registers a global TracerProvider that exports to the local
// Jaeger via OTLP/gRPC on port 4317. It returns the provider's Shutdown func
// so callers can defer a clean flush at exit.
func Init(ctx context.Context) (func(context.Context) error, error) {
	exp, err := otlptracegrpc.New(ctx,
		otlptracegrpc.WithInsecure(),
		otlptracegrpc.WithEndpoint("localhost:4317"),
	)
	if err != nil {
		return nil, err
	}

	tp := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(exp),
		sdktrace.WithResource(resource.NewWithAttributes(
			semconv.SchemaURL,
			semconv.ServiceName("fun-with-flags-go-chi"),
		)),
	)
	otel.SetTracerProvider(tp)
	return tp.Shutdown, nil
}
