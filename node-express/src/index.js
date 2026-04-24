import express from 'express';
import { OpenFeature } from '@openfeature/server-sdk';
import pino from 'pino';
import { initOpenFeature } from './openfeature.js';

const logger = pino({ name: 'fun-with-flags-demo-node' });

await initOpenFeature();

const app = express();
const client = OpenFeature.getClient();

app.get('/', async (req, res) => {
  const details = await client.getStringDetails('greetings', 'Hello World', {
    language: req.query.language,
  });
  res.json(details);
});

const port = Number(process.env.PORT ?? 8080);
app.listen(port, () => {
  logger.info({ port }, 'Server listening');
});
