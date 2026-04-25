import { AsyncLocalStorage } from 'node:async_hooks';
import { OpenFeature } from '@openfeature/server-sdk';

// Per-request store for query-derived context. Express middleware runs in the
// same async chain as the handler, so AsyncLocalStorage keeps the values safe
// across awaits without leaking between requests.
export const requestContext = new AsyncLocalStorage();

export function languageMiddleware(req, _res, next) {
  const language = typeof req.query.language === 'string' ? req.query.language : undefined;
  const userId = typeof req.query.userId === 'string' ? req.query.userId : undefined;

  // Build the OpenFeature transaction context. `targetingKey` is a top-level
  // field on the server-sdk evaluation context shape and is what fractional
  // targeting rules hash on for stable per-user bucketing.
  const ctx = { ...(language ? { language } : {}) };
  if (userId) ctx.targetingKey = userId;

  requestContext.run({ language, userId }, () => {
    if (Object.keys(ctx).length > 0) {
      OpenFeature.setTransactionContext(ctx, () => {
        next();
      });
    } else {
      next();
    }
  });
}
