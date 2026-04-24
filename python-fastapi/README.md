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

## Step 6 OpenTelemetry tracing

A flag evaluation without tracing is a black box — you know what variant came out, but not which rule matched, not which endpoint asked, and not how long the resolution took. Wiring in OpenTelemetry turns every evaluation into a span and, once FastAPI itself is instrumented, that span lands under the HTTP request span that triggered it. Now the value, the variant, the reason, and the parent route all show up in the same Jaeger trace.

```toml
dependencies = [
    "opentelemetry-api",
    "opentelemetry-sdk",
    "opentelemetry-exporter-otlp",
    "opentelemetry-instrumentation-fastapi",
    "openfeature-hooks-opentelemetry",
]
```

OTel setup lives in [`app/otel_setup.py`](app/otel_setup.py) — a tracer provider tagged with the service name, and an OTLP/gRPC exporter pointed at the shared Jaeger from [`../observability`](../observability):

```python
resource = Resource.create({"service.name": "fun-with-flags-python-fastapi"})
provider = TracerProvider(resource=resource)
provider.add_span_processor(BatchSpanProcessor(
    OTLPSpanExporter(endpoint="http://localhost:4317", insecure=True)
))
trace.set_tracer_provider(provider)
```

Wired into [`app/main.py`](app/main.py): call it from `lifespan`, instrument the FastAPI app, register the OpenFeature OTel hook:

```python
from openfeature.contrib.hook.opentelemetry import TracingHook
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor

@asynccontextmanager
async def lifespan(_: FastAPI):
    configure_otel()
    configure_openfeature()
    api.add_hooks([TracingHook()])
    yield
    shutdown_openfeature()

app = FastAPI(lifespan=lifespan)
FastAPIInstrumentor.instrument_app(app)
```

Run it end to end:

```bash
cd ../observability && docker compose up -d     # shared Jaeger + OTLP collector
cd -
.venv/bin/uvicorn app.main:app
curl http://localhost:8080/
```

Open <http://localhost:16686>, pick the `fun-with-flags-python-fastapi` service, click a trace — the flag evaluation span sits under the request span, with the flag key, variant, and reason attached.
