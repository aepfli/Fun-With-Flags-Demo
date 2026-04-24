package dev.openfeature.demo.quarkus;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.HookContext;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;

public class CustomHook implements Hook {
    private static final Logger LOG = Logger.getLogger(CustomHook.class);

    @Override
    public Optional<EvaluationContext> before(HookContext ctx, Map hints) {
        LOG.info("Before hook");
        return Hook.super.before(ctx, hints);
    }

    @Override
    public void after(HookContext ctx, FlagEvaluationDetails details, Map hints) {
        LOG.infof("After hook - %s", details.getReason());
        Hook.super.after(ctx, details, hints);
    }

    @Override
    public void error(HookContext ctx, Exception error, Map hints) {
        LOG.error("Error hook", error);
        Hook.super.error(ctx, error, hints);
    }

    @Override
    public void finallyAfter(HookContext ctx, FlagEvaluationDetails details, Map hints) {
        LOG.infof("Finally After hook - %s", details.getReason());
        Hook.super.finallyAfter(ctx, details, hints);
    }
}
