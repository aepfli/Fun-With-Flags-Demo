# Load Generator

Step 7 in spirit, even though it isn't a per-language code change: a small k6 container that drives traffic against whichever language variant you're running, **gated by an OpenFeature flag**. The flag itself is the on/off switch, which makes the demo a little recursive in a way I find satisfying — the feature-flag demo is itself feature-flagged.

## Why a flag, not a `docker compose stop`

In a workshop, the friction of "now switch to a terminal and stop the load" is enough to derail a thread of conversation. Editing one line in `flags.json` does not break flow, and the audience sees the same lever they have been learning about — flagd file watcher, hot reload, no restart — used to run the demo itself.

In Codespaces, this also means the loadgen container can stay up the whole session without burning CPU. It polls flagd once every two seconds while idle. Cheap.

## How it works

1. The k6 container reads the flag `loadgen_active` from flagd's HTTP evaluation API on `:8014`.
2. While the flag is `false` (default), each VU sleeps two seconds and polls again.
3. While the flag is `true`, each VU hits the language variant's `GET /` with a random `language` query parameter pulled from a deliberately uneven pool (more `de`, some `en`, a bit of everything else).
4. Five virtual users by default — enough to populate the Grafana dashboard, light enough to stay readable.

## Run it

You'll have three things running side by side: a language variant, the LGTM observability stack, and this loadgen.

```
# Terminal 1 — pick your language and start its app + flagd
cd java-spring && docker compose up -d && ./mvnw spring-boot:run

# Terminal 2 — observability
cd observability && docker compose up -d

# Terminal 3 — loadgen (idle by default)
cd loadgen && docker compose up -d
```

Open Grafana at <http://localhost:3000> (`admin` / `admin`) and load the **Fun With Flags — Feature Flag Metrics** dashboard.

## Turn the load on

Edit the running language's `flags.json` and flip `loadgen_active`'s `defaultVariant` from `"off"` to `"on"`. Save. Within seconds, the dashboard fills in.

```diff
   "loadgen_active": {
     "state": "ENABLED",
     "variants": { "off": false, "on": true },
-    "defaultVariant": "off"
+    "defaultVariant": "on"
   }
```

To stop the load: flip back to `"off"`. The k6 container stays up, idle.

## Pointing at a different language

By default the script hits `host.docker.internal:8080` (the language app's port). If you're running the app on a different host, override:

```
BASE_URL=http://my-app:8080 FLAGD_URL=http://my-flagd:8014 docker compose up -d
```

## Stop everything

```
docker compose down
```

## Why this is in `loadgen/` and not a `step/<folder>/7` branch

Nothing about this is language-specific. The k6 script is the same regardless of which language variant you're driving — same `GET /` contract on `:8080`, same flag in flagd. So instead of fanning out to five identical step branches, this lives at the repo root next to `observability/`. Each per-language README's Step 7 section points here.
