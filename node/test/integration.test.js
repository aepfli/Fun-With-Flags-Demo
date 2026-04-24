import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { GenericContainer } from 'testcontainers';
import request from 'supertest';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const flagsPath = path.resolve(__dirname, '..', 'flags.json');

// Give the flagd container plenty of time to pull on first run.
const SETUP_TIMEOUT_MS = 120_000;

describe('Fun-With-Flags Node integration', () => {
  let flagd;
  let app;

  beforeAll(async () => {
    flagd = await new GenericContainer('ghcr.io/open-feature/flagd:latest')
      .withExposedPorts(8013)
      .withBindMounts([
        {
          source: flagsPath,
          target: '/flags.json',
          mode: 'ro',
        },
      ])
      .withCommand(['start', '--uri', 'file:/flags.json'])
      .start();

    process.env.FLAGD_HOST = flagd.getHost();
    process.env.FLAGD_PORT = String(flagd.getMappedPort(8013));

    // Import lazily so the provider picks up the FLAGD_* env vars above.
    const { createApp } = await import('../src/index.js');
    app = await createApp();
  }, SETUP_TIMEOUT_MS);

  afterAll(async () => {
    if (flagd) {
      await flagd.stop();
    }
  });

  it('returns the default English greeting', async () => {
    const response = await request(app).get('/');
    expect(response.status).toBe(200);
    expect(response.body.value).toBe('Hello World!');
  });

  it('returns the German greeting when language=de', async () => {
    const response = await request(app).get('/').query({ language: 'de' });
    expect(response.status).toBe(200);
    expect(response.body.value).toBe('Hallo Welt!');
  });
});
