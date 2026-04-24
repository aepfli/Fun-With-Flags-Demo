import {
  OpenFeature,
  AsyncLocalStorageTransactionContextPropagator,
} from '@openfeature/server-sdk';
import { FlagdProvider } from '@openfeature/flagd-provider';
import { SpanHook } from '@openfeature/open-telemetry-hooks';
import { CustomHook } from './hook.js';

// Initialize the flagd provider in file (in-process offline) mode, register
// the custom hook, and set a global evaluation context with the Node version
// so targeting rules can match on it.
export async function initOpenFeature({
  offlineFlagSourcePath = './flags.json',
} = {}) {
  OpenFeature.setTransactionContextPropagator(
    new AsyncLocalStorageTransactionContextPropagator(),
  );

  OpenFeature.addHooks(new CustomHook());
  // Emits an OpenTelemetry span per flag evaluation, tagged with key/variant/reason,
  // and parents it under the active request span from auto-instrumentation.
  OpenFeature.addHooks(new SpanHook());

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
