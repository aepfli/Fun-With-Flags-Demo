"""OpenFeature wiring — InMemoryProvider with a `greetings` flag."""

from __future__ import annotations

import logging

from openfeature import api
from openfeature.provider.in_memory_provider import InMemoryFlag, InMemoryProvider

LOG = logging.getLogger("app.openfeature_setup")


def configure_openfeature() -> None:
    """Register the in-memory provider with hello/goodbye variants."""
    provider = InMemoryProvider(
        {
            "greetings": InMemoryFlag(
                default_variant="hello",
                variants={
                    "hello": "Hello World!",
                    "goodbye": "Goodbye World!",
                },
            ),
        }
    )
    api.set_provider(provider)
    LOG.info("OpenFeature configured with InMemoryProvider")


def shutdown_openfeature() -> None:
    """Cleanly shut the provider down."""
    try:
        api.shutdown()
    except Exception:  # pragma: no cover
        LOG.warning("OpenFeature shutdown raised", exc_info=True)
