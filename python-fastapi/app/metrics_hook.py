"""Custom OpenFeature hook that emits an OTel counter for every flag evaluation.

`openfeature-hooks-opentelemetry` 0.3.1 ships a `TracingHook` only — there is no
ready-made metrics hook yet. This module mirrors what such a hook would do:
on every successful evaluation it bumps a counter labelled with the flag key,
variant, and reason, so the observability stack can chart evaluation rate by
flag and by outcome.
"""

from __future__ import annotations

from typing import Any

from openfeature.flag_evaluation import FlagEvaluationDetails
from openfeature.hook import Hook, HookContext
from opentelemetry import metrics

# A single meter + counter is created at import time. The MeterProvider is
# registered in `app.otel_setup` before this hook ever runs.
_meter = metrics.get_meter("fun-with-flags-python-fastapi")
_eval_counter = _meter.create_counter(
    name="feature_flag.evaluation_requests_total",
    description="Total number of feature flag evaluations, labelled by flag/variant/reason.",
)


class FeatureFlagMetricsHook(Hook):
    """Counts every flag evaluation that reaches the `after` stage."""

    def after(
        self,
        hook_context: HookContext,
        details: FlagEvaluationDetails[Any],
        hints: dict[str, Any],
    ) -> None:
        _eval_counter.add(
            1,
            {
                "feature_flag.key": hook_context.flag_key,
                "feature_flag.variant": details.variant or "",
                "feature_flag.reason": str(details.reason)
                if details.reason is not None
                else "",
            },
        )
