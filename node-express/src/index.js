import express from 'express';
import { OpenFeature } from '@openfeature/server-sdk';
import pino from 'pino';
import { initOpenFeature } from './openfeature.js';
import { languageMiddleware } from './middleware.js';

const logger = pino({ name: 'fun-with-flags-demo-node' });

await initOpenFeature();

const app = express();
app.use(languageMiddleware);

const client = OpenFeature.getClient();

app.get('/', async (_req, res) => {
  const details = await client.getStringDetails('greetings', 'Hello World');
  res.json(details);
});

const port = Number(process.env.PORT ?? 8080);
app.listen(port, () => {
  logger.info({ port }, 'Server listening');
});
