# Fun With Flags — An OpenFeature Demo

[![CI](https://github.com/aepfli/Fun-With-Flags-Demo/actions/workflows/ci.yml/badge.svg)](https://github.com/aepfli/Fun-With-Flags-Demo/actions/workflows/ci.yml)
[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/aepfli/Fun-With-Flags-Demo)
[![Open in Dev Container](https://img.shields.io/static/v1?label=Dev%20Container&message=Open&color=blue&logo=visualstudiocode)](https://vscode.dev/redirect?url=vscode://ms-vscode-remote.remote-containers/cloneInVolume?url=https://github.com/aepfli/Fun-With-Flags-Demo)

I built this demo because OpenFeature talks tend to pick one language and leave the rest of the room guessing whether the same ideas apply to them. They do, and this repo proves it. I walk the exact same journey — add the SDK, swap providers, add context, add hooks, go remote — across Java Spring Boot, Java Quarkus, Go, Python (FastAPI), and Node.js (Express). Each folder is self-contained, so if you came for Go you never need to read the Python code, and the steps line up 1:1 so you can peek at another language when you want to compare.

I am a CNCF Ambassador, an OpenFeature maintainer, and I sit in the top three contributors to the project — so the opinions in these READMEs are mine, and I'll tell you when I think something is awkward.

## Pick your stack

| Folder | Stack |
| --- | --- |
| [`java/README.md`](java/README.md) | Java (Spring Boot) |
| [`quarkus/README.md`](quarkus/README.md) | Java (Quarkus) |
| [`go/README.md`](go/README.md) | Go |
| [`python/README.md`](python/README.md) | Python (FastAPI) |
| [`node/README.md`](node/README.md) | Node.js (Express) |

## The same learning arc

Every folder walks the same steps, 1.1 through 5.2: add the OpenFeature SDK, plug in the in-memory provider, switch to the flagd file provider, pass evaluation context dynamically, lift it into an interceptor or middleware, set it globally, attach hooks for observability, and finally talk to a real remote flagd — first via `docker compose`, then via Testcontainers so the test suite owns its own flagd. The language-specific detail (annotations, decorators, DI quirks) lives in each folder's README — I don't repeat it here.

## Running without installing anything

I don't want you fighting a JDK install during a 40-minute talk, so the repo ships a devcontainer. Two ways in:

- On GitHub, click **Code → Create codespace on main**. That is the zero-setup path.
- Or clone locally and in VS Code run **Dev Containers: Reopen in Container**.

Port `8080` is the app under test. Ports `8013`–`8016` are flagd (gRPC, HTTP, and the two sync variants). All of them are forwarded for you.

## Slides

The canonical, always-current deck lives at **<https://schrottner.at/openFeatureTalk>**. A snapshot is checked in as [`Fun with Flags.pdf`](Fun%20with%20Flags.pdf) at the repo root for workshops where the wifi does not cooperate.

## What's next

OpenTelemetry tracking is wired up on the `demo/with-tracking` branch, but only for the Java Spring Boot folder today. Bringing that hook + exporter story to the other four languages is the next thing on my list — PRs welcome.
