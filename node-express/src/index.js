import express from 'express';
import { OpenFeature } from '@openfeature/server-sdk';
import pino from 'pino';

const logger = pino({ name: 'fun-with-flags-demo-node' });

const app = express();
const client = OpenFeature.getClient();

app.get('/', async (_req, res) => {
  const details = await client.getStringDetails('greetings', 'Hello World');
  res.json(details);
});

const port = Number(process.env.PORT ?? 8080);
app.listen(port, () => {
  logger.info({ port }, 'Server listening');
});
