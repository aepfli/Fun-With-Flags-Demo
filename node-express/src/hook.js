import pino from 'pino';

const logger = pino({ name: 'custom-hook' });

// Mirrors the CustomHook from the Spring variant: before/after/error/finallyAfter
// on every flag evaluation so you can see the lifecycle in the logs.
export class CustomHook {
  before(hookContext, _hints) {
    logger.info({ flagKey: hookContext.flagKey }, 'Before hook');
    return undefined;
  }

  after(hookContext, details, _hints) {
    logger.info(
      { flagKey: hookContext.flagKey, reason: details.reason, variant: details.variant },
      'After hook',
    );
  }

  error(hookContext, error, _hints) {
    logger.error({ flagKey: hookContext.flagKey, err: error }, 'Error hook');
  }

  finallyAfter(hookContext, details, _hints) {
    logger.info(
      { flagKey: hookContext.flagKey, reason: details?.reason },
      'Finally after hook',
    );
  }
}
