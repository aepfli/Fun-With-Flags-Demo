"""OpenFeature wiring — FlagdProvider in FILE mode reading ./flags.json."""

from __future__ import annotations

import logging
import os

from openfeature import api
from openfeature.contrib.provider.flagd import FlagdProvider
from openfeature.contrib.provider.flagd.config import ResolverType

LOG = logging.getLogger("app.openfeature_setup")


def configure_openfeature() -> None:
    """Wire up the flagd FILE provider."""
    flags_path = os.environ.get("FLAGS_PATH", "./flags.json")
    provider = FlagdProvider(
        resolver_type=ResolverType.FILE,
        offline_flag_source_path=flags_path,
    )
    api.set_provider(provider)
    LOG.info("OpenFeature configured — flags file: %s", flags_path)


def shutdown_openfeature() -> None:
    """Cleanly shut the provider down so gRPC channels close."""
    try:
        api.shutdown()
    except Exception:  # pragma: no cover — best-effort cleanup
        LOG.warning("OpenFeature shutdown raised", exc_info=True)
