# Fun With Flags — An OpenFeature Demo

[![CI](https://github.com/aepfli/Fun-With-Flags-Demo/actions/workflows/ci.yml/badge.svg)](https://github.com/aepfli/Fun-With-Flags-Demo/actions/workflows/ci.yml)
[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/aepfli/Fun-With-Flags-Demo)
[![Open in Dev Container](https://img.shields.io/static/v1?label=Dev%20Container&message=Open&color=blue&logo=visualstudiocode)](https://vscode.dev/redirect?url=vscode://ms-vscode-remote.remote-containers/cloneInVolume?url=https://github.com/aepfli/Fun-With-Flags-Demo)

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

## The same learning arc

Every folder walks the same steps, 1.1 through 5.2: add the OpenFeature SDK, plug in the in-memory provider, switch to the flagd file provider, pass evaluation context dynamically, lift it into an interceptor or middleware, set it globally, attach hooks for observability, and finally talk to a real remote flagd — first via `docker compose`, then via Testcontainers so the test suite owns its own flagd. The language-specific detail (annotations, decorators, DI quirks) lives in each folder's README — I don't repeat it here.

## Running without installing anything

I don't want you fighting a JDK install during a 40-minute talk, so the repo ships a devcontainer. Two ways in:

- On GitHub, click **Code → Create codespace on main**. That is the zero-setup path.
- Or clone locally and in VS Code run **Dev Containers: Reopen in Container**.

Port `8080` is the app under test. Ports `8013`–`8016` are flagd (gRPC, HTTP, and the two sync variants). All of them are forwarded for you.

## Slides

The canonical, always-current deck lives at **<https://schrottner.at/openFeatureTalk>**. A snapshot is checked in as [`Fun with Flags.pdf`](Fun%20with%20Flags.pdf) at the repo root for workshops where the wifi does not cooperate.

## Step 6 — observability

Step 6 adds OpenTelemetry traces *and* metrics so every flag evaluation shows up alongside the rest of the app's telemetry. A single [`observability/`](observability/README.md) folder at the repo root holds a Grafana LGTM container — Grafana, Prometheus, Tempo, Loki, OTLP receivers, all in one image. One backend for every variant, one URL (<http://localhost:3000>) to open. Each language's step 6 lives on `step/<folder>/6` and adds the OTel hooks + exporter config.

The legacy `demo/with-tracking` branch is kept around for anyone who bookmarked it, but the step-6 branches supersede it.

## Step 7 — load generation, gated by a flag

[`loadgen/`](loadgen/README.md) ships a small k6 container that drives traffic against whichever language variant you're running, **only when the OpenFeature flag `loadgen_active` is on**. Flip `defaultVariant` from `"off"` to `"on"` in `flags.json`, watch the Grafana dashboard fill up, flip back to stop. The feature-flag demo is itself feature-flagged — the recursion is the point.

Nothing language-specific lives in step 7, so it doesn't fan out to per-language step branches: it's one shared folder that points at port 8080 of any of the variants.
