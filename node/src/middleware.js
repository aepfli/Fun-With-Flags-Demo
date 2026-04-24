import { AsyncLocalStorage } from 'node:async_hooks';
import { OpenFeature } from '@openfeature/server-sdk';

// Per-request store for the language query param. Express middleware runs in
// the same async chain as the handler, so AsyncLocalStorage keeps the value
// safe across awaits without leaking between requests.
export const requestContext = new AsyncLocalStorage();

export function languageMiddleware(req, _res, next) {
  const language = typeof req.query.language === 'string' ? req.query.language : undefined;

  // Run the rest of the request inside both the AsyncLocalStorage scope and
  // the OpenFeature transaction context, so any flag evaluation during the
  // request sees `language` automatically.
  requestContext.run({ language }, () => {
    const transactionContext = language ? { language } : {};
    OpenFeature.setTransactionContext(transactionContext, () => {
      next();
    });
  });
}
