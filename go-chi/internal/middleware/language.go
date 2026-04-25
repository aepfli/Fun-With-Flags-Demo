// Package middleware contains HTTP middleware that prepares an OpenFeature
// evaluation context for each request.
package middleware

import (
	"net/http"

	"github.com/open-feature/go-sdk/openfeature"
)

// Language reads the `language` and `userId` query parameters from the
// incoming request and attaches them to the request's context.Context as an
// OpenFeature transaction context. Handlers downstream can then read them by
// passing r.Context() into any client.*ValueDetails call.
//
// We use OpenFeature's transaction context because it is the idiomatic way to
// pass per-request evaluation context through Go's context.Context without
// touching the handler signature.
//
// When `userId` is supplied it becomes the EvaluationContext targeting key,
// which fractional targeting rules use to bucket users deterministically.
func Language(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attrs := map[string]any{}
		if lang := r.URL.Query().Get("language"); lang != "" {
			attrs["language"] = lang
		}
		userId := r.URL.Query().Get("userId")

		if len(attrs) == 0 && userId == "" {
			next.ServeHTTP(w, r)
			return
		}

		var ec openfeature.EvaluationContext
		if userId != "" {
			ec = openfeature.NewEvaluationContext(userId, attrs)
		} else {
			ec = openfeature.NewTargetlessEvaluationContext(attrs)
		}
		ctx := openfeature.WithTransactionContext(r.Context(), ec)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}
