# Observability Stack

Shared observability backend for step 6 across every language variant. One Jaeger container, OTLP receivers on the standard ports, UI at <http://localhost:16686>.

I kept it to a single container on purpose. Jaeger 1.35+ ships the OTLP receiver built in, so a separate OTel Collector is not needed to get traces flowing. When a workshop is ready to talk about Collector pipelines (sampling, routing, enrichment), that is the natural follow-up — but it is not what step 6 is trying to teach.

## Run it

```
cd observability
docker compose up -d
```

- Jaeger UI: <http://localhost:16686>
- OTLP gRPC: `localhost:4317`
- OTLP HTTP: `localhost:4318`

Every language's step 6 branch points its OTel exporter at `localhost:4317` (or `4318`) and writes a service name like `fun-with-flags-<language>`. Open the UI, pick the service, and you'll see one span per flag evaluation — with the flag key, variant, and reason attached as attributes courtesy of the OpenFeature OTel hook.

## Stop it

```
docker compose down
```

## Why this folder is separate

One observability stack serves every language variant. Keeping it here (and not in each `java-spring/`, `python-fastapi/`, …) means the attendee runs one compose file, not five, regardless of how many languages they try. The devcontainer forwards ports `16686`, `4317`, `4318` so this works in Codespaces too.
