// Package main is the entry point for the Fun With Flags Go demo. Step 3.1
// reads the `language` query parameter inside the handler and puts it into
// the OpenFeature evaluation context so the flagd targeting rule can match.
package main

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"os"

	"github.com/go-chi/chi/v5"
	"github.com/open-feature/go-sdk/openfeature"

	flagdsetup "github.com/openfeature/fun-with-flags-demo/go-chi/internal/flagd"
)

func main() {
	if err := flagdsetup.Init("./flags.json"); err != nil {
		slog.Error("OpenFeature init failed", "err", err)
		os.Exit(1)
	}

	client := openfeature.NewClient("demo")

	r := chi.NewRouter()
	r.Get("/", func(w http.ResponseWriter, r *http.Request) {
		attrs := map[string]any{}
		if lang := r.URL.Query().Get("language"); lang != "" {
			attrs["language"] = lang
		}
		evalCtx := openfeature.NewTargetlessEvaluationContext(attrs)
		details, err := client.StringValueDetails(r.Context(), "greetings", "No World", evalCtx)
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
