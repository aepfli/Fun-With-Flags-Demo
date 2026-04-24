"""FastAPI entry point — mirrors the Spring Boot end-state controller."""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from openfeature import api
from openfeature.evaluation_context import EvaluationContext

from app.middleware import LanguageMiddleware, language_ctx
from app.openfeature_setup import configure_openfeature, shutdown_openfeature

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)


@asynccontextmanager
async def lifespan(_: FastAPI):
    """FastAPI lifespan — set provider + global context on startup, tear down on stop."""
    configure_openfeature()
    try:
        yield
    finally:
        shutdown_openfeature()


app = FastAPI(lifespan=lifespan, title="Fun With Flags — Python (FastAPI)")
app.add_middleware(LanguageMiddleware)


@app.get("/")
def hello_world() -> dict:
    """Evaluate the `greetings` flag, with the request-scoped language in context."""
    language = language_ctx.get()
    if language is not None:
        api.set_transaction_context(
            EvaluationContext(attributes={"language": language})
        )

    client = api.get_client()
    details = client.get_string_details("greetings", "Hello World")

    return {
        "flag_key": details.flag_key,
        "value": details.value,
        "variant": details.variant,
        "reason": str(details.reason) if details.reason is not None else None,
    }
