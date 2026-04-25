# OpenFeature Go Demo

This is the Go variant of [Fun With Flags](../README.md). I added it so that people who write Go every day can see OpenFeature idiomatically rather than translating Java patterns in their head. The step-by-step arc mirrors the [Spring Boot variant](../java-spring/README.md) one-to-one.

Two things to flag up front as differences from the JVM versions:

- Per-request state lives on `context.Context`, not on a thread-local. The language middleware puts the value into the request context via `openfeature.WithTransactionContext`, and every handler hands `r.Context()` back to the client.
- Logging goes through `log/slog` — it is the standard library choice and it matches what most recent Go services ship with.

Run the app with `go run .`, then `curl http://localhost:8080/`. Requests for every step live in [`requests.http`](requests.http).

## Step 1.1 Add the OpenFeature SDK

```
go get github.com/open-feature/go-sdk
```

And the handler:

```go
client := openfeature.NewClient("fun-with-flags-go")
http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
    details, _ := client.StringValueDetails(r.Context(), "greetings", "Hello World", openfeature.EvaluationContext{})
    json.NewEncoder(w).Encode(details)
})
```

Run it, hit `/`, get `Hello World` — the fallback. No provider yet.

## Step 1.2 Provider initialization (in-memory)

```go
memprov := memprovider.NewInMemoryProvider(map[string]memprovider.InMemoryFlag{
    "greetings": {
        State:          memprovider.Enabled,
        DefaultVariant: "hello",
        Variants: map[string]any{
            "hello":   "Hello World!",
            "goodbye": "Goodbye World!",
        },
    },
})
openfeature.SetProviderAndWait(memprov)
```

In-memory is fine for the first demo. Everything else is too much for step 1.

## Step 2.1 Flagd file provider

```
go get github.com/open-feature/go-sdk-contrib/providers/flagd
```

I move the flag definition into [`flags.json`](flags.json) and swap the provider:

```go
provider, _ := flagdprovider.NewProvider(
    flagdprovider.WithFileResolver(),
    flagdprovider.WithOfflineFilePath("./flags.json"),
)
openfeature.SetProviderAndWait(provider)
```

Edit `flags.json`, change the `defaultVariant`, curl again. The value changes without a restart.

## Step 3.1 Dynamic context

The simplest form of targeting pulls `language` from the query string and puts it in the evaluation context of the call:

```go
lang := r.URL.Query().Get("language")
ec := openfeature.NewTargetlessEvaluationContext(map[string]any{"language": lang})
details, _ := client.StringValueDetails(r.Context(), "greetings", "Hello World", ec)
```

The targeting rule in `flags.json` maps `language == "de"` to the `hallo` variant.

## Step 3.1.1 chi middleware

Populating the context inside every handler is going to get old fast. I use a chi middleware that stashes the language on `r.Context()` via OpenFeature's transaction context helper:

```go
func Language(next http.Handler) http.Handler {
    return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        if lang := r.URL.Query().Get("language"); lang != "" {
            ec := openfeature.NewTargetlessEvaluationContext(map[string]any{"language": lang})
            r = r.WithContext(openfeature.WithTransactionContext(r.Context(), ec))
        }
        next.ServeHTTP(w, r)
    })
}
```

With this wired in, the handler goes back to a one-liner: it passes `r.Context()` to `StringValueDetails` and the SDK finds the transaction context there.

## Step 3.2 Global evaluation context

Runtime version is the same for every request, so it belongs on the global context, set once at startup:

```go
openfeature.SetEvaluationContext(openfeature.NewEvaluationContext("", map[string]any{
    "goVersion": strings.TrimPrefix(runtime.Version(), "go"),
}))
```

Two gotchas worth naming:

- `runtime.Version()` returns values like `"go1.22.3"`. The `sem_ver` targeting in `flags.json` wants a plain semver, so I strip the `"go"` prefix before putting it into the context.
- The targeting rule matches `goVersion >= "1.20.0"` and returns the `gopher` variant when it matches.

## Step 4 Hooks

```go
type Custom struct{ openfeature.UnimplementedHook }

func (Custom) Before(_ context.Context, hc openfeature.HookContext, _ openfeature.HookHints) (*openfeature.EvaluationContext, error) {
    slog.Info("Before hook", "flag", hc.FlagKey())
    return nil, nil
}

func (Custom) After(_ context.Context, hc openfeature.HookContext, d openfeature.InterfaceEvaluationDetails, _ openfeature.HookHints) error {
    slog.Info("After hook", "flag", hc.FlagKey(), "variant", d.Variant, "reason", d.Reason)
    return nil
}
// Error and Finally follow the same pattern.
```

Registered once with `openfeature.AddHooks(hook.Custom{})`. Embedding `UnimplementedHook` keeps me from having to implement every phase when I only care about a few.

## Step 5.1 Remote flagd via docker compose

File mode is fine for demos. In real deployments flagd runs as its own process. I spin it up with [`docker-compose.yaml`](docker-compose.yaml), then switch the resolver to RPC:

```go
provider, _ := flagdprovider.NewProvider(
    flagdprovider.WithRPCResolver(),
    flagdprovider.WithHost("localhost"),
    flagdprovider.WithPort(8013),
)
```

RPC mode calls flagd on every evaluation. `flagdprovider.WithInProcessResolver()` fetches the flag set and watches for updates — cheaper for hot paths.

## Step 5.2 Testing against flagd without docker compose

Testcontainers-go owns the container lifecycle inside the test:

```go
req := testcontainers.ContainerRequest{
    Image:        "ghcr.io/open-feature/flagd:latest",
    ExposedPorts: []string{"8013/tcp"},
    Cmd:          []string{"start", "--uri", "file:/flags.json"},
    Files:        []testcontainers.ContainerFile{{HostFilePath: "./flags.json", ContainerFilePath: "/flags.json"}},
    WaitingFor:   wait.ForListeningPort("8013/tcp"),
}
c, _ := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{ContainerRequest: req, Started: true})
defer c.Terminate(ctx)
```

Run `go test ./...`. The container starts, the tests pass, it shuts down. No second terminal.

## Step 6 OpenTelemetry observability

Every flag evaluation becomes a span in Tempo, nested under the HTTP request span that triggered it. The code lives on [`step/go-chi/6`](https://github.com/aepfli/Fun-With-Flags-Demo/tree/step/go-chi/6); the shared Grafana LGTM container lives in [`../observability/`](../observability/README.md).

Run `cd ../observability && docker compose up -d`, check out `step/go-chi/6`, and start the app with `go run .`. Grafana UI at <http://localhost:3000>, open the **Fun With Flags — Feature Flag Metrics** dashboard or use Explore → Tempo to pick the `fun-with-flags-go-chi` service.
