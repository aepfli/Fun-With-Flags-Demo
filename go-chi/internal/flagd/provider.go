// Package flagd sets up the flagd FILE-mode provider pointing at the local
// flags.json file. Later steps evolve this into adding hooks and a global
// evaluation context.
package flagd

import (
	"fmt"

	flagdprovider "github.com/open-feature/go-sdk-contrib/providers/flagd/pkg"
	"github.com/open-feature/go-sdk/openfeature"
)

// Init configures OpenFeature with a flagd FILE-mode provider pointing at the
// given flags.json path.
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

	return nil
}
