import { OpenFeature, InMemoryProvider } from '@openfeature/server-sdk';

// Minimal in-memory provider: enough to serve the `greetings` flag with a
// couple of variants so step 1 works end-to-end without flagd.
export async function initOpenFeature() {
  const provider = new InMemoryProvider({
    greetings: {
      defaultVariant: 'hello',
      variants: {
        hello: 'Hello World!',
        goodbye: 'Goodbye World!',
      },
      disabled: false,
    },
  });

  await OpenFeature.setProviderAndWait(provider);

  return provider;
}
