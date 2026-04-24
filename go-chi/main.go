// Package main is the entry point for the Fun With Flags Go demo. Step 1.1
// imports the OpenFeature SDK but does not configure any provider, so the
// default NoopProvider is used and every evaluation falls back to the default.
package main

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"os"

	"github.com/go-chi/chi/v5"
	"github.com/open-feature/go-sdk/openfeature"
)

func main() {
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
