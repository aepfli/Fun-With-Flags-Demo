# OpenFeature Python Demo

This is the Python variant of [Fun With Flags](../README.md), built on FastAPI. I added it because a lot of the OpenFeature conversations I have in workshops come from Python teams, and having a canonical Python end-to-end example makes those conversations much shorter. The step-by-step arc mirrors the [Spring Boot variant](../java-spring/README.md) one-to-one.

Two things to flag up front as differences from the JVM versions:

- Per-request state lives in `contextvars.ContextVar`, not in a thread-local. `ContextVar` is async-safe, which matters once FastAPI is running under `asyncio`.
- Startup and shutdown hang off the FastAPI `lifespan` context manager — that is where the provider gets registered and the global evaluation context gets set.

Run the app with `.venv/bin/uvicorn app.main:app --reload`, then `curl http://localhost:8080/`. Requests for every step live in [`requests.http`](requests.http).

## Step 1.1 Add the OpenFeature SDK

```toml
[project]
dependencies = [
    "fastapi",
    "uvicorn[standard]",
    "openfeature-sdk",
]
```

In the FastAPI app:

```python
from openfeature import api

@app.get("/")
def hello_world():
    client = api.get_client()
    details = client.get_string_details("greetings", "Hello World")
    return {"value": details.value, "variant": details.variant}
```

Run it, hit `/`, get `Hello World` — the fallback. No provider yet.

## Step 1.2 Provider initialization (in-memory)

```python
from openfeature.provider.in_memory_provider import InMemoryProvider, InMemoryFlag

flags = {
    "greetings": InMemoryFlag(
        default_variant="hello",
        variants={"hello": "Hello World!", "goodbye": "Goodbye World!"},
    ),
}
api.set_provider(InMemoryProvider(flags))
```

Good enough for step 1, not good enough for step 2.

## Step 2.1 Flagd file provider

```toml
dependencies = [
    "openfeature-provider-flagd",
]
```

I move the flag definition into [`flags.json`](flags.json) and swap the provider in `openfeature_setup.py`:

```python
from openfeature.contrib.provider.flagd import FlagdProvider, ResolverType

api.set_provider(FlagdProvider(
    resolver_type=ResolverType.FILE,
    offline_flag_source_path="./flags.json",
))
```

Edit `flags.json`, change the `defaultVariant`, hit the endpoint again. The value changes without a restart.

## Step 3.1 Dynamic context

The simplest form of targeting pulls `language` from the query string and puts it in the evaluation context of the call:

```python
@app.get("/")
def hello_world(language: str | None = None):
    ctx = EvaluationContext(attributes={"language": language}) if language else None
    details = api.get_client().get_string_details("greetings", "Hello World", ctx)
    return {"value": details.value}
```

The targeting rule in `flags.json` maps `language == "de"` to the `hallo` variant.

## Step 3.1.1 FastAPI middleware + ContextVar

Wiring context inside every endpoint is going to get old fast. A middleware handles it once, using a `ContextVar` so the value is async-safe:

```python
language_ctx: ContextVar[str | None] = ContextVar("language", default=None)

class LanguageMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request, call_next):
        token = language_ctx.set(request.query_params.get("language"))
        try:
            return await call_next(request)
        finally:
            language_ctx.reset(token)
```

The endpoint reads `language_ctx.get()` and writes it onto the OpenFeature transaction context before the evaluation. The key thing I want to call out: `ContextVar` is the right tool here, not a plain global — `asyncio` tasks each get their own copy.

## Step 3.2 Global evaluation context

Runtime version does not change per request, so it belongs on the global context set once at startup:

```python
import sys

py_version = f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}"
api.set_evaluation_context(EvaluationContext(attributes={"pythonVersion": py_version}))
```

The targeting rule in `flags.json` matches `pythonVersion >= "3.10.0"` and returns the `pythonista` variant.

## Step 4 Hooks

```python
from openfeature.hook import Hook
import logging

class CustomHook(Hook):
    def before(self, ctx, hints): logging.info("Before hook %s", ctx.flag_key)
    def after(self, ctx, details, hints): logging.info("After hook %s %s", details.variant, details.reason)
    def error(self, ctx, exc, hints): logging.error("Error hook", exc_info=exc)
    def finally_after(self, ctx, details, hints): logging.info("Finally %s", details.reason)
```

Registered once in `openfeature_setup.py` via `api.add_hooks([CustomHook()])`.

## Step 5.1 Remote flagd via docker compose

File mode is fine for demos. In real deployments flagd runs as its own process. I spin it up with [`docker-compose.yaml`](docker-compose.yaml), then switch the resolver to RPC:

```python
api.set_provider(FlagdProvider(resolver_type=ResolverType.RPC, host="localhost", port=8013))
```

RPC calls flagd on every evaluation. `ResolverType.IN_PROCESS` fetches the flag set and watches for updates — cheaper for hot paths.

## Step 5.2 Testing against flagd without docker compose

Testcontainers-python owns the container lifecycle inside the test, so `pytest` does not need a second terminal:

```python
from testcontainers.core.container import DockerContainer

@pytest.fixture(scope="module")
def flagd():
    container = (
        DockerContainer("ghcr.io/open-feature/flagd:latest")
        .with_volume_mapping("./flags.json", "/flags.json")
        .with_command("start --uri file:/flags.json")
        .with_exposed_ports(8013)
    )
    with container:
        yield container
```

Run `.venv/bin/pytest`. The container starts, tests pass, it shuts down.

## Step 6 OpenTelemetry observability

Every flag evaluation becomes a span in Tempo, nested under the HTTP request span that triggered it. The code lives on [`step/python-fastapi/6`](https://github.com/aepfli/Fun-With-Flags-Demo/tree/step/python-fastapi/6); the shared Grafana LGTM container lives in [`../observability/`](../observability/README.md).

Run `cd ../observability && docker compose up -d`, check out `step/python-fastapi/6`, and start the app with `.venv/bin/uvicorn app.main:app`. Grafana UI at <http://localhost:3000>, open the **Fun With Flags — Feature Flag Metrics** dashboard or use Explore → Tempo to pick the `fun-with-flags-python-fastapi` service.

## Step 7 Load generation

Once you've got step 6 wired up, the dashboard is empty until something drives traffic. The shared [`../loadgen/`](../loadgen/README.md) folder runs a small k6 container that hits `GET /` with a mix of `language` values — **but only while the OpenFeature flag `loadgen_active` is set to `on`**. The flag is already in this folder's `flags.json` (`defaultVariant: "off"`); flip it to `"on"`, the dashboard fills, flip back to idle the load. The feature-flag demo, feature-flagged.
