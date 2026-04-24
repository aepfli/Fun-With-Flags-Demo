// Package middleware contains HTTP middleware that prepares an OpenFeature
// evaluation context for each request.
package middleware

import (
	"net/http"

	"github.com/open-feature/go-sdk/openfeature"
)

// Language reads the `language` query parameter from the incoming request and
// attaches it to the request's context.Context as an OpenFeature transaction
// context. Handlers downstream can then read it by passing r.Context() into
// any client.StringValueDetails call.
//
// We use OpenFeature's transaction context because it is the idiomatic way to
// pass per-request evaluation context through Go's context.Context without
// touching the handler signature.
func Language(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		lang := r.URL.Query().Get("language")
		if lang != "" {
			ec := openfeature.NewTargetlessEvaluationContext(map[string]any{
				"language": lang,
			})
			ctx := openfeature.WithTransactionContext(r.Context(), ec)
			r = r.WithContext(ctx)
		}
		next.ServeHTTP(w, r)
	})
}
