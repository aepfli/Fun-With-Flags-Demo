// Package main is the entry point for the Fun With Flags Go demo. Step 1.2
// wires an in-memory OpenFeature provider with a single "greetings" flag so
// the evaluation returns a real value without any external dependency.
package main

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"os"

	"github.com/go-chi/chi/v5"
	"github.com/open-feature/go-sdk/openfeature"
	"github.com/open-feature/go-sdk/openfeature/memprovider"
)

func main() {
	provider := memprovider.NewInMemoryProvider(map[string]memprovider.InMemoryFlag{
		"greetings": {
			State:          memprovider.Enabled,
			DefaultVariant: "hello",
			Variants: map[string]any{
				"hello":   "Hello World!",
				"goodbye": "Goodbye World!",
			},
		},
	})
	if err := openfeature.SetProviderAndWait(provider); err != nil {
		slog.Error("OpenFeature init failed", "err", err)
		os.Exit(1)
	}

	client := openfeature.NewClient("demo")

	r := chi.NewRouter()
	r.Get("/", func(w http.ResponseWriter, r *http.Request) {
		details, err := client.StringValueDetails(r.Context(), "greetings", "No World", openfeature.EvaluationContext{})
		if err != nil {
			slog.Error("evaluation failed", "err", err)
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(details)
	})

	addr := ":8080"
	slog.Info("listening", "addr", addr)
	if err := http.ListenAndServe(addr, r); err != nil {
		slog.Error("server stopped", "err", err)
		os.Exit(1)
	}
}
