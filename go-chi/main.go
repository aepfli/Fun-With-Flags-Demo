// Package main is the entry point for the Fun With Flags Go demo. It wires the
// flagd provider, adds the language middleware and serves a single GET /
// endpoint that returns the evaluation of the "greetings" flag.
package main

import (
	"context"
	"encoding/json"
	"log/slog"
	"math/rand/v2"
	"net/http"
	"os"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/open-feature/go-sdk/openfeature"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"

	flagdsetup "github.com/openfeature/fun-with-flags-demo/go-chi/internal/flagd"
	"github.com/openfeature/fun-with-flags-demo/go-chi/internal/middleware"
	otelsetup "github.com/openfeature/fun-with-flags-demo/go-chi/internal/otel"
)

func main() {
	ctx := context.Background()

	shutdown, err := otelsetup.Init(ctx)
	if err != nil {
		slog.Error("OpenTelemetry init failed", "err", err)
		os.Exit(1)
	}
	defer func() {
		if err := shutdown(context.Background()); err != nil {
			slog.Error("OpenTelemetry shutdown failed", "err", err)
		}
	}()

	if err := flagdsetup.Init("./flags.json"); err != nil {
		slog.Error("OpenFeature init failed", "err", err)
		os.Exit(1)
	}

	client := openfeature.NewClient("fun-with-flags-go")

	r := chi.NewRouter()
	r.Use(middleware.Language)
	r.Get("/", func(w http.ResponseWriter, req *http.Request) {
		newAlgo, _ := client.BooleanValue(req.Context(), "new_greeting_algo", false, openfeature.EvaluationContext{})
		if newAlgo {
			time.Sleep(200 * time.Millisecond)
			if rand.Float64() < 0.1 {
				http.Error(w, "simulated failure in new_greeting_algo", http.StatusInternalServerError)
				return
			}
		}
		details, err := client.StringValueDetails(req.Context(), "greetings", "Hello World", openfeature.EvaluationContext{})
		if err != nil {
			slog.Error("evaluation failed", "err", err)
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(details)
	})

	addr := ":8080"
	slog.Info("listening", "addr", addr)
	if err := http.ListenAndServe(addr, otelhttp.NewHandler(r, "http.server")); err != nil {
		slog.Error("server stopped", "err", err)
		os.Exit(1)
	}
}
