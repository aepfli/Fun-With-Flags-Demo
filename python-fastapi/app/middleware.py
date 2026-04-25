"""FastAPI middleware that lifts request-scoped context onto the OpenFeature transaction context.

The middleware reads `language` and `userId` from the query string and writes
them onto the OpenFeature *transaction* context: `userId` becomes the
`targetingKey` (used by fractional rollouts), `language` is carried as an
attribute. The context is reset after the request so values cannot leak
between concurrent requests served by the same event loop.
"""

from __future__ import annotations

from contextvars import ContextVar

from openfeature import api
from openfeature.evaluation_context import EvaluationContext
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.types import ASGIApp

# Kept for backwards-compatibility with code that still reads `language_ctx`.
language_ctx: ContextVar[str | None] = ContextVar("language_ctx", default=None)


class LanguageMiddleware(BaseHTTPMiddleware):
    """Pulls `language` + `userId` from query params onto the OpenFeature transaction context."""

    def __init__(self, app: ASGIApp) -> None:
        super().__init__(app)

    async def dispatch(self, request: Request, call_next):
        language = request.query_params.get("language")
        user_id = request.query_params.get("userId")

        attrs = {"language": language} if language else {}
        if user_id:
            ec = EvaluationContext(targeting_key=user_id, attributes=attrs)
        else:
            ec = EvaluationContext(attributes=attrs)
        api.set_transaction_context(ec)

        token = language_ctx.set(language)
        try:
            return await call_next(request)
        finally:
            # Reset so a stray reference can't leak between requests.
            language_ctx.reset(token)
            api.set_transaction_context(EvaluationContext())
