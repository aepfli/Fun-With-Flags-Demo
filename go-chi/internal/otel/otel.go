// Package otelsetup configures the OpenTelemetry tracer and meter providers
// used by the go-chi demo. It wires OTLP/gRPC exporters pointed at the shared
// LGTM stack in ../observability so flag evaluation spans land in Tempo and
// flag evaluation metrics land in Mimir/Prometheus out of the box.
package otelsetup

import (
	"context"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.26.0"
)

// Init builds and registers a global TracerProvider and MeterProvider that
// export to the local LGTM stack via OTLP/gRPC on port 4317. It returns a
// shutdown func that flushes BOTH providers, so callers can defer a clean
// flush at exit.
func Init(ctx context.Context) (func(context.Context) error, error) {
	res := resource.NewWithAttributes(
		semconv.SchemaURL,
		semconv.ServiceName("fun-with-flags-go-chi"),
	)

	traceExp, err := otlptracegrpc.New(ctx,
		otlptracegrpc.WithInsecure(),
		otlptracegrpc.WithEndpoint("localhost:4317"),
	)
	if err != nil {
		return nil, err
	}

	tp := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(traceExp),
		sdktrace.WithResource(res),
	)
	otel.SetTracerProvider(tp)

	metricExp, err := otlpmetricgrpc.New(ctx,
		otlpmetricgrpc.WithInsecure(),
		otlpmetricgrpc.WithEndpoint("localhost:4317"),
	)
	if err != nil {
		return nil, err
	}

	mp := sdkmetric.NewMeterProvider(
		sdkmetric.WithReader(sdkmetric.NewPeriodicReader(metricExp, sdkmetric.WithInterval(10*time.Second))),
		sdkmetric.WithResource(res),
	)
	otel.SetMeterProvider(mp)

	shutdown := func(ctx context.Context) error {
		tErr := tp.Shutdown(ctx)
		mErr := mp.Shutdown(ctx)
		if tErr != nil {
			return tErr
		}
		return mErr
	}

	return shutdown, nil
}
