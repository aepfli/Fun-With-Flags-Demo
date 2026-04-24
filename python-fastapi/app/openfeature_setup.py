"""OpenFeature wiring — FlagdProvider in FILE mode with a ContextVar propagator."""

from __future__ import annotations

import logging
import os

from openfeature import api
from openfeature.contrib.provider.flagd import FlagdProvider
from openfeature.contrib.provider.flagd.config import ResolverType
from openfeature.transaction_context import ContextVarsTransactionContextPropagator

LOG = logging.getLogger("app.openfeature_setup")


def configure_openfeature() -> None:
    """Wire up the flagd FILE provider and the ContextVar transaction propagator."""
    flags_path = os.environ.get("FLAGS_PATH", "./flags.json")
    provider = FlagdProvider(
        resolver_type=ResolverType.FILE,
        offline_flag_source_path=flags_path,
    )
    api.set_provider(provider)

    # ContextVar-based propagator — asyncio-safe equivalent of ThreadLocal.
    api.set_transaction_context_propagator(ContextVarsTransactionContextPropagator())

    LOG.info("OpenFeature configured — flags file: %s", flags_path)


def shutdown_openfeature() -> None:
    """Cleanly shut the provider down so gRPC channels close."""
    try:
        api.shutdown()
    except Exception:  # pragma: no cover — best-effort cleanup
        LOG.warning("OpenFeature shutdown raised", exc_info=True)
