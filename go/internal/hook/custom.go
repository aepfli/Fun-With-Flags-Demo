// Package hook contains a small OpenFeature hook that logs every phase of a
// flag evaluation using log/slog. It mirrors the CustomHook used in the Spring
// Boot variant.
package hook

import (
	"context"
	"log/slog"

	"github.com/open-feature/go-sdk/openfeature"
)

// Custom is an OpenFeature hook that logs Before, After, Error and Finally.
type Custom struct {
	openfeature.UnimplementedHook
}

// Before runs before a flag evaluation. We return nil so we do not alter the
// evaluation context.
func (Custom) Before(_ context.Context, hc openfeature.HookContext, _ openfeature.HookHints) (*openfeature.EvaluationContext, error) {
	slog.Info("Before hook", "flag", hc.FlagKey())
	return nil, nil
}

// After runs after a successful evaluation.
func (Custom) After(_ context.Context, hc openfeature.HookContext, details openfeature.InterfaceEvaluationDetails, _ openfeature.HookHints) error {
	slog.Info("After hook",
		"flag", hc.FlagKey(),
		"variant", details.Variant,
		"reason", details.Reason)
	return nil
}

// Error runs if the evaluation failed.
func (Custom) Error(_ context.Context, hc openfeature.HookContext, err error, _ openfeature.HookHints) {
	slog.Error("Error hook", "flag", hc.FlagKey(), "err", err)
}

// Finally always runs at the end.
func (Custom) Finally(_ context.Context, hc openfeature.HookContext, details openfeature.InterfaceEvaluationDetails, _ openfeature.HookHints) {
	slog.Info("Finally hook",
		"flag", hc.FlagKey(),
		"reason", details.Reason)
}
