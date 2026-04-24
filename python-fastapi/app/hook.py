"""Custom OpenFeature hook that logs every lifecycle stage of a flag evaluation."""

from __future__ import annotations

import logging
from typing import Any

from openfeature.evaluation_context import EvaluationContext
from openfeature.flag_evaluation import FlagEvaluationDetails
from openfeature.hook import Hook, HookContext

LOG = logging.getLogger("app.hook")


class CustomHook(Hook):
    """Mirrors the Spring Boot CustomHook — logs before/after/error/finally_after."""

    def before(
        self, hook_context: HookContext, hints: dict[str, Any]
    ) -> EvaluationContext | None:
        LOG.info("Before hook — flag=%s", hook_context.flag_key)
        return None

    def after(
        self,
        hook_context: HookContext,
        details: FlagEvaluationDetails[Any],
        hints: dict[str, Any],
    ) -> None:
        LOG.info("After hook — reason=%s variant=%s", details.reason, details.variant)

    def error(
        self,
        hook_context: HookContext,
        exception: Exception,
        hints: dict[str, Any],
    ) -> None:
        LOG.error("Error hook — flag=%s", hook_context.flag_key, exc_info=exception)

    def finally_after(
        self,
        hook_context: HookContext,
        details: FlagEvaluationDetails[Any],
        hints: dict[str, Any],
    ) -> None:
        LOG.info("Finally After hook — reason=%s", details.reason)
