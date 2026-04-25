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

## Step 6 OpenTelemetry traces and metrics

A flag evaluation without telemetry is a black box — you know what variant came out, but not which rule matched, not which endpoint asked, and not how often each variant is being served. OpenTelemetry handles both halves of that question: every evaluation becomes a span (parented under the HTTP request span thanks to `FastAPIInstrumentor`), and a counter ticks up so you can chart evaluation rate by flag, variant, and reason.

```toml
dependencies = [
    "opentelemetry-api",
    "opentelemetry-sdk",
    "opentelemetry-exporter-otlp",
    "opentelemetry-instrumentation-fastapi",
    "openfeature-hooks-opentelemetry",
]
```

OTel setup lives in [`app/otel_setup.py`](app/otel_setup.py) — a tracer provider and a meter provider, both tagged with the service name and both shipping OTLP/gRPC to the shared LGTM stack from [`../observability`](../observability):

```python
resource = Resource.create({"service.name": "fun-with-flags-python-fastapi"})

provider = TracerProvider(resource=resource)
provider.add_span_processor(BatchSpanProcessor(
    OTLPSpanExporter(endpoint="http://localhost:4317", insecure=True)
))
trace.set_tracer_provider(provider)

reader = PeriodicExportingMetricReader(
    OTLPMetricExporter(endpoint="http://localhost:4317", insecure=True),
    export_interval_millis=10_000,
)
metrics.set_meter_provider(MeterProvider(resource=resource, metric_readers=[reader]))
```

`openfeature-hooks-opentelemetry` 0.3.1 only ships a `TracingHook`, so [`app/metrics_hook.py`](app/metrics_hook.py) supplies the metrics counterpart — a tiny `Hook` that bumps a counter on every `after()`:

```python
class FeatureFlagMetricsHook(Hook):
    def after(self, hook_context, details, hints):
        _eval_counter.add(1, {
            "feature_flag.key": hook_context.flag_key,
            "feature_flag.variant": details.variant or "",
            "feature_flag.reason": str(details.reason) if details.reason is not None else "",
        })
```

Wired into [`app/main.py`](app/main.py): call `configure_otel()` from `lifespan`, instrument the FastAPI app, register both hooks:

```python
from openfeature.contrib.hook.opentelemetry import TracingHook
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from app.metrics_hook import FeatureFlagMetricsHook

@asynccontextmanager
async def lifespan(_: FastAPI):
    configure_otel()
    configure_openfeature()
    api.add_hooks([TracingHook(), FeatureFlagMetricsHook()])
    yield
    shutdown_openfeature()

app = FastAPI(lifespan=lifespan)
FastAPIInstrumentor.instrument_app(app)
```

Run it end to end:

```bash
cd ../observability && docker compose up -d     # shared LGTM stack (Grafana + Tempo + Mimir)
cd -
.venv/bin/uvicorn app.main:app
curl http://localhost:8080/
```

Open Grafana at <http://localhost:3000> (login `admin` / `admin`):

- **Traces** — *Explore → Tempo*, search for service `fun-with-flags-python-fastapi`. Each trace has the flag evaluation span sitting under the request span, with the flag key, variant, and reason attached.
- **Metrics** — open the **Fun With Flags — Feature Flag Metrics** dashboard. The export interval is 10s, so the first datapoint shows up roughly 10–15s after the first request.

## Step 7 Progressive rollout of a risky new code path

The motivation here is a real-world one: imagine a "new greeting algorithm" that the team wants to ship. It is materially slower (200ms extra) and is known to be flaky during burn-in (10% error rate). We do *not* want to flip it on for everyone in one go, and we definitely do not want to redeploy each time we change the percentage. A boolean fractional flag covers both: the percentage lives in [`flags.json`](flags.json), and the SLO panels in Grafana tell us whether to keep ramping or roll back.

The endpoint reads a second flag and short-circuits to the slow + occasionally-failing code path when it resolves to `true`:

```python
@app.get("/")
def hello_world():
    client = api.get_client()
    new_algo = client.get_boolean_value("new_greeting_algo", False)
    if new_algo:
        time.sleep(0.2)
        if random.random() < 0.1:
            raise HTTPException(status_code=500, detail="simulated failure in new_greeting_algo")
    details = client.get_string_details("greetings", "Hello World")
    return {"flag_key": details.flag_key, "value": details.value, "variant": details.variant, "reason": str(details.reason) if details.reason else None}
```

Fractional targeting needs a stable bucket key per user, so the middleware now also lifts `userId` out of the query string and writes it onto the OpenFeature transaction context as the `targetingKey`:

```python
class LanguageMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request, call_next):
        language = request.query_params.get("language")
        user_id = request.query_params.get("userId")
        attrs = {"language": language} if language else {}
        ec = EvaluationContext(targeting_key=user_id, attributes=attrs) if user_id \
             else EvaluationContext(attributes=attrs)
        api.set_transaction_context(ec)
        try:
            return await call_next(request)
        finally:
            api.set_transaction_context(EvaluationContext())
```

The flag definition in [`flags.json`](flags.json) starts at 0% — same shape as the Java/Go variants:

```json
"new_greeting_algo": {
  "state": "ENABLED",
  "variants": { "off": false, "on": true },
  "defaultVariant": "off",
  "targeting": {
    "fractional": [
      ["off", 100],
      ["on", 0]
    ]
  }
}
```

### Ramp it up

1. **0% → 10%** — edit `flags.json` and bump `["on", 0]` to `["on", 10]` (drop the `off` weight to `90`). flagd watches the file, no restart needed. Drive load with `for i in $(seq 1 200); do curl -s "http://localhost:8080/?userId=user-$i" >/dev/null; done`. About one in ten requests now sleeps 200ms, and a fraction of those return 500.
2. **Watch Grafana** — *Fun With Flags — Feature Flag Metrics* shows the `new_greeting_algo=on` slice climbing toward 10%. The Tempo traces for the slow path carry the flag span with `reason=TARGETING_MATCH` and `variant=on`. P95 latency on the request span jumps for the matched slice; the overall P95 should rise about a tenth of that.
3. **10% → 50%** — same edit, `["on", 50]`. Same observability checks. If the error-rate panel for the `on` variant is still sitting at the expected ~10% and nothing else is on fire, keep going.
4. **Roll back** — set `["on", 0]` (and `off` back to `100`). The next evaluation flips everyone back to the safe path, no deploy, no restart, no code change. That is the whole point of the exercise.
