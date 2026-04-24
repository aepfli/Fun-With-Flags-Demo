"""FastAPI entry point — SDK imported, no provider configured yet."""

from __future__ import annotations

from fastapi import FastAPI
from openfeature import api

app = FastAPI(title="Fun With Flags — Python (FastAPI)")


@app.get("/")
def hello_world() -> dict:
    """Evaluate the `greetings` flag with no provider configured (returns default)."""
    client = api.get_client()
    details = client.get_string_details("greetings", "Hello World")

    return {
        "flag_key": details.flag_key,
        "value": details.value,
        "variant": details.variant,
        "reason": str(details.reason) if details.reason is not None else None,
    }
