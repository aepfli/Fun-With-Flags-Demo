import { OpenFeature } from '@openfeature/server-sdk';
import { FlagdProvider } from '@openfeature/flagd-provider';

// Flagd in file (in-process offline) mode: reads flags.json on startup and
// watches it for changes, no daemon required.
export async function initOpenFeature({
  offlineFlagSourcePath = './flags.json',
} = {}) {
  const provider = new FlagdProvider({
    resolverType: 'in-process',
    offlineFlagSourcePath,
  });

  await OpenFeature.setProviderAndWait(provider);

  return provider;
}
