# Observability Stack

One container, both halves of the picture: traces and metrics for every flag evaluation, side by side in Grafana.

I picked [`grafana/otel-lgtm`](https://github.com/grafana/docker-otel-lgtm) on purpose. It bundles Grafana, Prometheus/Mimir (metrics), Tempo (traces), and Loki (logs) into a single image with OTLP receivers built in. One process to remember, one URL to open, one less thing standing between an attendee and a working demo. A "real" deployment would split these out — but a demo that takes ten minutes to set up isn't a demo any more.

## Run it

```
cd observability
docker compose up -d
```

- Grafana UI: <http://localhost:3000> — log in with `admin` / `admin`, skip the password change prompt
- OTLP gRPC: `localhost:4317`
- OTLP HTTP: `localhost:4318`

## What you'll see

The container ships data sources for Prometheus, Tempo, and Loki already wired up. Open Grafana → **Dashboards** to find the **Fun With Flags — Feature Flag Metrics** dashboard (loaded from [`dashboards/feature-flags.json`](dashboards/feature-flags.json)). Four panels:

- Flag evaluations per second, broken out by flag key
- Variant distribution over the last 5 minutes (pie)
- Evaluation error rate
- Per-service evaluation rate — useful when more than one variant is running

For traces, open **Explore** → pick the **Tempo** datasource → search by service name (`fun-with-flags-java-spring`, `fun-with-flags-go-chi`, …). Each flag evaluation appears as a span event nested inside the request that triggered it.

## Stop it

```
docker compose down
```

## Why this folder is separate

One observability stack serves every language variant. Keeping it here (and not in each `java-spring/`, `python-fastapi/`, …) means an attendee runs one compose file no matter how many languages they try. The devcontainer forwards ports `3000`, `4317`, `4318` so this works in Codespaces too.
