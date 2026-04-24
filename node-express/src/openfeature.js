import {
  OpenFeature,
  AsyncLocalStorageTransactionContextPropagator,
} from '@openfeature/server-sdk';
import { FlagdProvider } from '@openfeature/flagd-provider';

// Flagd in file (in-process offline) mode, with a transaction context
// propagator for per-request context and a global context carrying the Node
// runtime version so targeting rules can match on it.
export async function initOpenFeature({
  offlineFlagSourcePath = './flags.json',
} = {}) {
  OpenFeature.setTransactionContextPropagator(
    new AsyncLocalStorageTransactionContextPropagator(),
  );

  OpenFeature.setContext({
    nodeVersion: process.versions.node,
  });

  const provider = new FlagdProvider({
    resolverType: 'in-process',
    offlineFlagSourcePath,
  });

  await OpenFeature.setProviderAndWait(provider);

  return provider;
}
