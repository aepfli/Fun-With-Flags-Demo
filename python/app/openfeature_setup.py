"""OpenFeature wiring — provider, hooks, and the global evaluation context.

This module is called from the FastAPI lifespan handler at startup and shutdown.
"""

from __future__ import annotations

import logging
import os
import sys

from openfeature import api
from openfeature.contrib.provider.flagd import FlagdProvider
from openfeature.contrib.provider.flagd.config import ResolverType
from openfeature.evaluation_context import EvaluationContext
from openfeature.transaction_context import ContextVarsTransactionContextPropagator

from app.hook import CustomHook

LOG = logging.getLogger("app.openfeature_setup")


def python_version_string() -> str:
    """Return `sys.version_info` assembled as `major.minor.micro`."""
    info = sys.version_info
    return f"{info.major}.{info.minor}.{info.micro}"


def configure_openfeature() -> None:
    """Wire up the flagd FILE provider, register the hook, set global context."""

    # Flagd FILE mode — reads ./flags.json from the process working directory.
    flags_path = os.environ.get("FLAGS_PATH", "./flags.json")
    provider = FlagdProvider(
        resolver_type=ResolverType.FILE,
        offline_flag_source_path=flags_path,
    )
    api.set_provider(provider)

    # ContextVar-based propagator — asyncio-safe equivalent of ThreadLocal.
    api.set_transaction_context_propagator(ContextVarsTransactionContextPropagator())

    # Custom hook logs every evaluation.
    api.add_hooks([CustomHook()])

    # Global evaluation context — pythonVersion is compared against a sem_ver target.
    api.set_evaluation_context(
        EvaluationContext(attributes={"pythonVersion": python_version_string()})
    )

    LOG.info("OpenFeature configured — flags file: %s", flags_path)


def shutdown_openfeature() -> None:
    """Cleanly shut the provider down so gRPC channels close."""
    try:
        api.shutdown()
    except Exception:  # pragma: no cover — best-effort cleanup
        LOG.warning("OpenFeature shutdown raised", exc_info=True)
