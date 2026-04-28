# Fun With Flags — An OpenFeature Demo

[![CI](https://github.com/aepfli/Fun-With-Flags-Demo/actions/workflows/ci.yml/badge.svg)](https://github.com/aepfli/Fun-With-Flags-Demo/actions/workflows/ci.yml)
[![Beginner](https://img.shields.io/static/v1?label=Codespace&message=Beginner&color=22c55e&logo=github)](https://codespaces.new/aepfli/Fun-With-Flags-Demo?devcontainer_path=.devcontainer%2Fbeginner%2Fdevcontainer.json)
[![Intermediate](https://img.shields.io/static/v1?label=Codespace&message=Intermediate&color=eab308&logo=github)](https://codespaces.new/aepfli/Fun-With-Flags-Demo?devcontainer_path=.devcontainer%2Fintermediate%2Fdevcontainer.json)
[![Expert](https://img.shields.io/static/v1?label=Codespace&message=Expert&color=ef4444&logo=github)](https://codespaces.new/aepfli/Fun-With-Flags-Demo?devcontainer_path=.devcontainer%2Fexpert%2Fdevcontainer.json)

I built this demo because OpenFeature talks tend to pick one language and leave the rest of the room guessing whether the same ideas apply to them. They do, and this repo proves it. I walk the exact same journey — add the SDK, swap providers, add context, add hooks, go remote — across Java Spring Boot, Java Quarkus, Go, Python (FastAPI), and Node.js (Express). Each folder is self-contained, so if you came for Go you never need to read the Python code, and the steps line up 1:1 so you can peek at another language when you want to compare.

I am a CNCF Ambassador, an OpenFeature maintainer, and I sit in the top three contributors to the project — so the opinions in these READMEs are mine, and I'll tell you when I think something is awkward.

## Pick your stack

| Folder | Stack |
| --- | --- |
| [`java-spring/README.md`](java-spring/README.md) | Java + Spring Boot |
| [`java-quarkus/README.md`](java-quarkus/README.md) | Java + Quarkus |
| [`go-chi/README.md`](go-chi/README.md) | Go + chi |
| [`python-fastapi/README.md`](python-fastapi/README.md) | Python + FastAPI |
| [`node-express/README.md`](node-express/README.md) | Node.js + Express |

The folder name always reads `<language>-<framework>`, so adding a new variant later (`java-micronaut`, `python-flask`, `go-gin`, …) drops in alongside the existing ones without breaking the pattern.

## Pick your phase

The demo is a three-act adventure. Each phase is a separate Codespaces config so you only get the tools you need — fast boot for the early phases, the full operational stack when you're ready for it.

| Phase | Steps | What it teaches | Codespace |
| --- | --- | --- | --- |
| **Beginner** | 1.1, 1.2 | OpenFeature SDK basics, in-memory provider | language toolchains, no Docker |
| **Intermediate** | 2.1 → 5.2 | flagd, targeting, interceptors, hooks, remote flagd, Testcontainers | + Docker-in-Docker, flagd ports forwarded |
| **Expert** | 6, 7 | OpenTelemetry traces & metrics, progressive rollout with consequences | + LGTM stack and loadgen ports |

Click a Codespaces badge above to launch the matching environment. Locally: clone the repo, then in VS Code run **Dev Containers: Reopen in Container** and pick the phase from the prompt.

Port `8080` is the app. `8013` is flagd's gRPC eval (the gRPC-Gateway HTTP/JSON paths ride on the same port via cmux); `8014` is flagd management — Prometheus `/metrics`, `/healthz`, `/readyz`; `8015` is the sync gRPC stream that powers `IN_PROCESS`; `8016` is flagd's OFREP HTTP eval API. `3000` is Grafana. `4317` / `4318` are the OTLP receivers. Each phase forwards only the ports it needs.

## Slides

The canonical, always-current deck lives at **<https://schrottner.at/openFeatureTalk>**. A snapshot is checked in as [`Fun with Flags.pdf`](Fun%20with%20Flags.pdf) at the repo root for workshops where the wifi does not cooperate.

## Step 6 — observability

Step 6 adds OpenTelemetry traces *and* metrics so every flag evaluation shows up alongside the rest of the app's telemetry. A single [`observability/`](observability/README.md) folder at the repo root holds a Grafana LGTM container — Grafana, Prometheus, Tempo, Loki, OTLP receivers, all in one image. One backend for every variant, one URL (<http://localhost:3000>) to open. Each language's step 6 lives on `step/<folder>/6` and adds the OTel hooks + exporter config.

The legacy `demo/with-tracking` branch is kept around for anyone who bookmarked it, but the step-6 branches supersede it.

## Step 7 — progressive rollout with consequences

A new greeting algorithm is rolling out. It is slower (200ms) and 10% of the time it errors. Step 7's job is to roll it out gradually, watch what happens in Grafana, and roll back without redeploying.

Two moving parts:

- **[`loadgen/`](loadgen/README.md)** — k6 container that drives traffic, **gated by an OpenFeature flag** (`loadgen_active`). Flip the flag, the load runs; flip it back, it idles. The feature-flag demo is itself feature-flagged.
- **`step/<folder>/7`** branches — per-language code change adding `new_greeting_algo` (slow + error path) and reading `?userId=…` as the OpenFeature `targetingKey` so the fractional rollout buckets are stable per user.

Each language's README walks the operational story: ramp the percentage in `flags.json`, watch the HTTP latency and 5xx panels respond, roll back the second something looks bad.
