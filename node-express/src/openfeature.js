import {
  OpenFeature,
  AsyncLocalStorageTransactionContextPropagator,
} from '@openfeature/server-sdk';
import { FlagdProvider } from '@openfeature/flagd-provider';

// Flagd in file (in-process offline) mode, plus an AsyncLocalStorage-based
// transaction context propagator so per-request context flows into flag
// evaluations automatically.
export async function initOpenFeature({
  offlineFlagSourcePath = './flags.json',
} = {}) {
  OpenFeature.setTransactionContextPropagator(
    new AsyncLocalStorageTransactionContextPropagator(),
  );

  const provider = new FlagdProvider({
    resolverType: 'in-process',
    offlineFlagSourcePath,
  });

  await OpenFeature.setProviderAndWait(provider);

  return provider;
}
