// Package main is the entry point for the Fun With Flags Go demo. It wires the
// flagd provider, adds the language middleware and serves a single GET /
// endpoint that returns the evaluation of the "greetings" flag.
package main

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"os"

	"github.com/go-chi/chi/v5"
	"github.com/open-feature/go-sdk/openfeature"

	flagdsetup "github.com/openfeature/fun-with-flags-demo/go/internal/flagd"
	"github.com/openfeature/fun-with-flags-demo/go/internal/middleware"
)

func main() {
	if err := flagdsetup.Init("./flags.json"); err != nil {
		slog.Error("OpenFeature init failed", "err", err)
		os.Exit(1)
	}

	client := openfeature.NewClient("fun-with-flags-go")

	r := chi.NewRouter()
	r.Use(middleware.Language)
	r.Get("/", func(w http.ResponseWriter, r *http.Request) {
		details, err := client.StringValueDetails(r.Context(), "greetings", "Hello World", openfeature.EvaluationContext{})
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
