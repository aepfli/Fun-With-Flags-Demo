// Must be first: boots the OpenTelemetry SDK so auto-instrumentation can patch
// Express/HTTP before they are required below.
import './otel.js';

import { setTimeout as sleep } from 'node:timers/promises';
import express from 'express';
import { OpenFeature } from '@openfeature/server-sdk';
import pino from 'pino';
import { initOpenFeature } from './openfeature.js';
import { languageMiddleware } from './middleware.js';

const logger = pino({ name: 'fun-with-flags-demo-node' });

export async function createApp() {
  await initOpenFeature();

  const app = express();
  app.use(languageMiddleware);

  const client = OpenFeature.getClient();

  app.get('/', async (_req, res) => {
    const newAlgo = await client.getBooleanValue('new_greeting_algo', false);
    if (newAlgo) {
      // Simulate a slower path on the new algorithm and inject a 10% error
      // rate so progressive rollout has something to react to.
      await sleep(200);
      if (Math.random() < 0.1) {
        return res.status(500).json({ error: 'simulated failure in new_greeting_algo' });
      }
    }
    const details = await client.getStringDetails('greetings', 'Hello World');
    res.json(details);
  });

  return app;
}

// Only start a server when this file is the entry point, so tests can import
// createApp without side effects.
const isMain = import.meta.url === `file://${process.argv[1]}`;
if (isMain) {
  const port = Number(process.env.PORT ?? 8080);
  createApp()
    .then((app) => {
      app.listen(port, () => {
        logger.info({ port }, 'Server listening');
      });
    })
    .catch((err) => {
      logger.error({ err }, 'Failed to start');
      process.exit(1);
    });
}
