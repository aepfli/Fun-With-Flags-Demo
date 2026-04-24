// Package flagd sets up the flagd provider and installs a global evaluation
// context that carries the Go runtime version so the sem_ver targeting rule
// in flags.json can match.
package flagd

import (
	"fmt"
	"runtime"
	"strings"

	flagdprovider "github.com/open-feature/go-sdk-contrib/providers/flagd/pkg"
	"github.com/open-feature/go-sdk/openfeature"
)

// Init configures OpenFeature with a flagd FILE-mode provider pointing at
// ./flags.json, and sets a global evaluation context with the Go runtime
// version.
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

	openfeature.SetEvaluationContext(openfeature.NewEvaluationContext("", map[string]any{
		"goVersion": strings.TrimPrefix(runtime.Version(), "go"),
	}))

	return nil
}
