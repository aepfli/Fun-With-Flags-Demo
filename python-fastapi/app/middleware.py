"""FastAPI middleware that lifts the `language` query param into a ContextVar.

Using a `contextvars.ContextVar` keeps the value scoped per-request even in an
async event loop, which is the Python equivalent of Spring Boot's
ThreadLocalTransactionContextPropagator.
"""

from __future__ import annotations

from contextvars import ContextVar

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.types import ASGIApp

# Module-level ContextVar — the OpenFeature propagator reads this in the handler.
language_ctx: ContextVar[str | None] = ContextVar("language_ctx", default=None)


class LanguageMiddleware(BaseHTTPMiddleware):
    """Pulls the `language` query parameter and stashes it in `language_ctx`."""

    def __init__(self, app: ASGIApp) -> None:
        super().__init__(app)

    async def dispatch(self, request: Request, call_next):
        language = request.query_params.get("language")
        token = language_ctx.set(language)
        try:
            return await call_next(request)
        finally:
            # Reset so a stray reference can't leak between requests.
            language_ctx.reset(token)
