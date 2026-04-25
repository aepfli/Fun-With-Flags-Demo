"""FastAPI entry point — mirrors the Spring Boot end-state controller."""

from __future__ import annotations

import logging
import random
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from openfeature import api
from openfeature.contrib.hook.opentelemetry import TracingHook
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor

from app.metrics_hook import FeatureFlagMetricsHook
from app.middleware import LanguageMiddleware
from app.openfeature_setup import configure_openfeature, shutdown_openfeature
from app.otel_setup import configure_otel

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)


@asynccontextmanager
async def lifespan(_: FastAPI):
    """FastAPI lifespan — set provider + global context on startup, tear down on stop."""
    configure_otel()
    configure_openfeature()
    # OTel hooks: TracingHook emits a span per evaluation, the custom metrics
    # hook bumps a counter labelled by flag/variant/reason.
    api.add_hooks([TracingHook(), FeatureFlagMetricsHook()])
    try:
        yield
    finally:
        shutdown_openfeature()


app = FastAPI(lifespan=lifespan, title="Fun With Flags — Python (FastAPI)")
app.add_middleware(LanguageMiddleware)
# Auto-instrument the HTTP layer — request spans become parents of flag spans.
FastAPIInstrumentor.instrument_app(app)


@app.get("/")
def hello_world() -> dict:
    """Evaluate the `greetings` flag, with the request-scoped context already set by middleware.

    A second flag — `new_greeting_algo` — gates a deliberately-slower-and-flakier
    code path so the workshop can demo a progressive rollout (latency + 10% error
    injection) controlled entirely by `flags.json`.
    """
    client = api.get_client()

    new_algo = client.get_boolean_value("new_greeting_algo", False)
    if new_algo:
        time.sleep(0.2)
        if random.random() < 0.1:
            raise HTTPException(
                status_code=500,
                detail="simulated failure in new_greeting_algo",
            )

    details = client.get_string_details("greetings", "Hello World")

    return {
        "flag_key": details.flag_key,
        "value": details.value,
        "variant": details.variant,
        "reason": str(details.reason) if details.reason is not None else None,
    }
