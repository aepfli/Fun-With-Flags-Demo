// Package flagd sets up the flagd provider, registers our custom hook and
// installs a global evaluation context that carries the Go runtime version.
//
// Keeping the setup in one place mirrors the OpenFeatureConfig pattern used in
// the Spring Boot variant, so the two demos read the same way side by side.
package flagd

import (
	"fmt"
	"runtime"
	"strings"

	otelhook "github.com/open-feature/go-sdk-contrib/hooks/open-telemetry/pkg"
	flagdprovider "github.com/open-feature/go-sdk-contrib/providers/flagd/pkg"
	"github.com/open-feature/go-sdk/openfeature"

	"github.com/openfeature/fun-with-flags-demo/go-chi/internal/hook"
)

// Init configures OpenFeature with a flagd FILE-mode provider pointing at
// ./flags.json, attaches the custom logging hook, and sets a global evaluation
// context with the Go runtime version.
//
// runtime.Version() returns values like "go1.22.3"; the sem_ver targeting in
// flags.json expects a plain semver, so we strip the "go" prefix before
// putting it into context.
func Init(flagsPath string) error {
	provider, err := flagdprovider.NewProvider(
		flagdprovider.WithFileResolver(),
		flagdprovider.WithOfflineFilePath(flagsPath),
	)
	if err != nil {
		return fmt.Errorf("build flagd provider: %w", err)
	}

	if err := openfeature.SetProviderAndWait(provider); err != nil {
		return fmt.Errorf("set flagd provider: %w", err)
	}

	openfeature.AddHooks(hook.Custom{})
	openfeature.AddHooks(otelhook.NewTracesHook())

	metricsHook, err := otelhook.NewMetricsHook()
	if err != nil {
		return fmt.Errorf("build OTel metrics hook: %w", err)
	}
	openfeature.AddHooks(metricsHook)

	openfeature.SetEvaluationContext(openfeature.NewEvaluationContext("", map[string]any{
		"goVersion": strings.TrimPrefix(runtime.Version(), "go"),
	}))

	return nil
}
