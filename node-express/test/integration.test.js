import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { GenericContainer, Wait } from 'testcontainers';
import { OpenFeature } from '@openfeature/server-sdk';
import { FlagdProvider } from '@openfeature/flagd-provider';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const flagsPath = path.resolve(__dirname, '..', 'flags.json');
const flagsContent = fs.readFileSync(flagsPath, 'utf8');

// Give the flagd container plenty of time to pull on first run.
const SETUP_TIMEOUT_MS = 120_000;

describe('Fun-With-Flags Node integration', () => {
  let container;

  beforeAll(async () => {
    // withCopyContentToContainer streams the file via a tar archive at
    // create-time. withCopyFilesToContainer / withBindMounts both silently
    // drop single-file mounts on CI's Docker daemon; inline content dodges
    // that by never touching the host-mount code path.
    container = await new GenericContainer('ghcr.io/open-feature/flagd:latest')
      .withExposedPorts(8013)
      .withCopyContentToContainer([
        { content: flagsContent, target: '/flags.json' },
      ])
      .withCommand(['start', '--uri', 'file:/flags.json'])
      .withWaitStrategy(Wait.forListeningPorts())
      .withStartupTimeout(60_000)
      .start();

    const provider = new FlagdProvider({
      resolverType: 'rpc',
      host: container.getHost(),
      port: container.getMappedPort(8013),
    });
    await OpenFeature.setProviderAndWait(provider);
  }, SETUP_TIMEOUT_MS);

  afterAll(async () => {
    await OpenFeature.close();
    if (container) {
      await container.stop();
    }
  });

  it('returns the default English greeting', async () => {
    const details = await OpenFeature.getClient().getStringDetails('greetings', 'Hello World');
    expect(details.value).toBe('Hello World!');
  });

  it('returns the German greeting when language=de', async () => {
    const details = await OpenFeature.getClient().getStringDetails('greetings', 'Hello World', { language: 'de' });
    expect(details.value).toBe('Hallo Welt!');
  });
});
