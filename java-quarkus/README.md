# OpenFeature Quarkus Demo

This is the Quarkus variant of [Fun With Flags](../README.md). I wrote it because a lot of Java shops run Quarkus rather than Spring Boot, and I did not want anyone to have to translate a Spring tutorial in their head while also learning OpenFeature for the first time. The step-by-step arc mirrors the [Spring Boot variant](../java-spring/README.md) one-to-one, so you can read them side by side.

Two things to flag up front as differences from the Spring version:

- Wiring happens via CDI (`@ApplicationScoped`, `@PostConstruct` on a startup bean) instead of a Spring `@Configuration`.
- The language interceptor is a `ContainerRequestFilter` from JAX-RS, not a Spring `HandlerInterceptor`. The transaction context propagator is the same `ThreadLocalTransactionContextPropagator` though, because OpenFeature itself does not change between the two frameworks.

Run the app with `./mvnw quarkus:dev`, then `curl http://localhost:8080/`. Requests for every step live in [`requests.http`](requests.http).

## Step 1.1 Add the OpenFeature SDK

The goal here is small: add the SDK, evaluate one flag, see that without a provider we get the fallback value.

```xml
<dependency>
    <groupId>dev.openfeature</groupId>
    <artifactId>sdk</artifactId>
    <version>1.14.2</version>
</dependency>
```

In the JAX-RS resource:

```java
@Path("/")
public class IndexResource {
    @GET
    public FlagEvaluationDetails<String> index() {
        Client client = OpenFeatureAPI.getInstance().getClient();
        return client.getStringDetails("greetings", "No World");
    }
}
```

Run the app, hit `/`, get `No World`. That is the fallback, and it is exactly what you should see before a provider is wired in.

## Step 1.2 Provider initialization (in-memory)

Next I give the SDK a provider so it has something to evaluate against. For the first pass I use `InMemoryProvider` so there is no file, no network, just a map.

```java
@Startup
@ApplicationScoped
public class OpenFeatureStartup {
    @PostConstruct
    public void initProvider() {
        HashMap<String, Flag<?>> flags = new HashMap<>();
        flags.put("greetings",
                Flag.builder()
                        .variant("goodbye", "Goodbye World!")
                        .variant("hello", "Hello World!")
                        .defaultVariant("hello")
                        .build());
        OpenFeatureAPI.getInstance().setProviderAndWait(new InMemoryProvider(flags));
    }
}
```

Yes, building flags in Java code is tedious — that is why in step 2 I move the definition to a file.

## Step 2.1 Flagd file provider

I want the flag definition out of code and into something I can edit live in front of an audience. Flagd's file resolver is the smallest change that gets me there.

```xml
<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>flagd</artifactId>
    <version>0.11.8</version>
</dependency>
```

Flags live in [`flags.json`](flags.json) at the project root. The startup bean now builds a `FlagdProvider` in FILE mode:

```java
FlagdOptions options = FlagdOptions.builder()
        .resolverType(Config.Resolver.FILE)
        .offlineFlagSourcePath("./flags.json")
        .build();
OpenFeatureAPI.getInstance().setProviderAndWait(new FlagdProvider(options));
```

Edit `flags.json`, change the `defaultVariant`, hit the endpoint again. The new value comes through without a restart.

## Step 3.1 Dynamic context

Flags that always return the same value are not that interesting. Targeting lets the flag value depend on something about the request. I start with the simplest case — read a `language` query parameter and put it into the evaluation context.

```java
@GET
public FlagEvaluationDetails<String> index(@QueryParam("language") String language) {
    Map<String, Value> attributes = new HashMap<>();
    if (language != null) attributes.put("language", new Value(language));
    return OpenFeatureAPI.getInstance().getClient()
            .getStringDetails("greetings", "Hello World", new ImmutableContext(attributes));
}
```

The targeting rule in `flags.json` maps `language == "de"` to the `hallo` variant.

## Step 3.1.1 Request filter

Wiring the context inside every resource is going to get old fast. JAX-RS gives me a `ContainerRequestFilter` for exactly this — set the transaction context on the way in, clear it on the way out, and the resource goes back to being a one-liner.

```java
@Provider
public class LanguageRequestFilter implements ContainerRequestFilter {
    @Override
    public void filter(ContainerRequestContext ctx) {
        String language = ctx.getUriInfo().getQueryParameters().getFirst("language");
        if (language != null) {
            Map<String, Value> attrs = Map.of("language", new Value(language));
            OpenFeatureAPI.getInstance().setTransactionContext(new ImmutableContext(attrs));
        }
    }
}
```

For this to work the `OpenFeatureStartup` registers a `ThreadLocalTransactionContextPropagator`.

## Step 3.2 Global evaluation context

Some targeting keys do not change between requests — runtime version, region, deployment stage. Those belong on the global context, set once at startup.

```java
String version = System.getProperty("quarkus.platform.version", "3.0.0");
api.setEvaluationContext(new ImmutableContext(Map.of("quarkusVersion", new Value(version))));
```

The targeting in `flags.json` checks `quarkusVersion >= 3.0.0` and returns the `springer` variant when it matches. Yes, the variant is named after Spring — I kept the name from the original demo so the side-by-side comparison stays clean.

> Note: Quarkus does not expose its own version via a clean runtime API the way Spring does with `SpringVersion.getVersion()`. The build injects the platform version as a system property; I fall back to `"3.0.0"` so the targeting keeps working in tests that do not set the property.

## Step 4 Hooks

Hooks run around every evaluation. They are the extension point I reach for when I want logging, metrics, or tracing without touching every call site.

```java
public class CustomHook implements Hook {
    private static final Logger LOG = Logger.getLogger(CustomHook.class);

    @Override public Optional<EvaluationContext> before(HookContext ctx, Map hints) { LOG.info("Before hook"); return Optional.empty(); }
    @Override public void after(HookContext ctx, FlagEvaluationDetails details, Map hints) { LOG.infof("After hook - %s", details.getReason()); }
    @Override public void error(HookContext ctx, Exception error, Map hints) { LOG.error("Error hook", error); }
    @Override public void finallyAfter(HookContext ctx, FlagEvaluationDetails details, Map hints) { LOG.infof("Finally - %s", details.getReason()); }
}
```

Registered once in `OpenFeatureStartup` via `api.addHooks(new CustomHook())`.

## Step 5.1 Remote flagd via docker compose

File mode is fine for demos on a laptop. In a real setup flagd runs as its own process and the app talks to it over gRPC or HTTP. I spin flagd up with [`docker-compose.yaml`](docker-compose.yaml), then switch the provider to RPC mode:

```java
FlagdOptions options = FlagdOptions.builder()
        .resolverType(Config.Resolver.RPC)
        .build();
```

In RPC mode every evaluation hits flagd. `IN_PROCESS` mode fetches the flag set and watches for updates — good for hot paths.

## Step 5.2 Testing against flagd without docker compose

I do not want an attendee to have to remember `docker compose up` in a second terminal, and I do not want CI to have to coordinate a sidecar. Testcontainers owns the container lifecycle inside the test itself.

```java
@Testcontainers
@QuarkusTest
class FlagdIntegrationTest {
    @Container
    static GenericContainer<?> flagd = new GenericContainer<>("ghcr.io/open-feature/flagd:latest")
            .withExposedPorts(8013)
            .withFileSystemBind("./flags.json", "/flags.json", BindMode.READ_ONLY)
            .withCommand("start", "--uri", "file:/flags.json");

    @Test void germanVariant() {
        given().queryParam("language", "de").get("/")
                .then().body(containsString("Hallo Welt"));
    }
}
```

Run `./mvnw verify` — the container starts, the test passes, the container stops. No second terminal.

## Step 6 OpenTelemetry traces and metrics

Hooks are a nice place to log flag evaluations, but logs only tell part of the story. Once requests start fanning out across services, I want two things: a trace that shows each flag evaluation as a span nested inside the HTTP request that caused it, and metrics that count evaluations by flag key, variant, and reason so I can spot regressions on a dashboard. OpenFeature ships an OTel hook for each signal — `TracesHook` and `MetricsHook` — and Quarkus's `quarkus-opentelemetry` extension wires up the tracer and meter providers plus the OTLP exporter out of the box.

Add the Quarkus OTel extension and the OpenFeature OTel hook to `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-opentelemetry</artifactId>
</dependency>
<dependency>
    <groupId>dev.openfeature.contrib.hooks</groupId>
    <artifactId>otel</artifactId>
    <version>3.2.1</version>
</dependency>
```

Point the exporter at the shared LGTM stack in `src/main/resources/application.properties` and turn metrics on:

```properties
quarkus.application.name=fun-with-flags-java-quarkus
quarkus.otel.exporter.otlp.endpoint=http://localhost:4317
quarkus.otel.exporter.otlp.protocol=grpc
quarkus.otel.metrics.enabled=true
quarkus.otel.metric.export.interval=10s
```

Register both hooks alongside the existing `CustomHook` in `OpenFeatureStartup.initProvider()`. `MetricsHook` needs the `OpenTelemetry` instance — Quarkus produces one via CDI, so inject it with `@Inject OpenTelemetry openTelemetry`:

```java
api.addHooks(new CustomHook());
api.addHooks(new TracesHook());
api.addHooks(new MetricsHook(openTelemetry));
```

> Note: the 3.x line of the OTel hook renamed `OpenTelemetryHook` to `TracesHook` and added the sibling `MetricsHook`. If you are pinning an older 1.x version, the class is still `OpenTelemetryHook`.

Start the shared LGTM backend (Grafana + Loki + Tempo + Mimir), then run the app:

```bash
cd ../observability && docker compose up -d
cd ../java-quarkus && ./mvnw quarkus:dev
```

Hit `curl http://localhost:8080/` a few times, then open Grafana at <http://localhost:3000> (login `admin` / `admin`). Two views to look at:

- **Traces** — go to **Explore → Tempo**, pick the `fun-with-flags-java-quarkus` service, and you will see each HTTP request with a nested flag-evaluation span carrying the flag key, variant, and reason as attributes.
- **Metrics** — open the **Fun With Flags — Feature Flag Metrics** dashboard for evaluation counts broken down by flag key, variant, and reason.

Metrics export on a 10s interval, so expect a 10–15s delay between the first request and the first datapoint showing up on the dashboard.

## Step 7 Progressive rollout

Now I get to the part of the talk that justifies the whole exercise: deploy is not the same as release. The new code can ship to production weeks before any user actually runs it, and when it misbehaves I roll it back in flagd without redeploying anything.

The setup is a contrived "new greeting algorithm" — slower (200ms artificial latency) and flaky (10% chance to return a 500). I want it behind a fractional flag so I can ramp traffic 0 → 10 → 50 → 100 while watching the latency and error panels in Grafana.

`IndexResource` reads the boolean and branches:

```java
@GET
@Produces(MediaType.APPLICATION_JSON)
public Response index() {
    Client client = OpenFeatureAPI.getInstance().getClient();
    boolean newAlgo = client.getBooleanValue("new_greeting_algo", false);
    if (newAlgo) {
        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
            return Response.status(500).entity("simulated failure in new_greeting_algo").build();
        }
    }
    return Response.ok(client.getStringDetails("greetings", "No World")).build();
}
```

The fractional operator in flagd needs a stable bucketing key per user, otherwise the same caller bounces between variants on every request and the rollout looks like noise. I extend the request filter to pull `userId` from the query string and set it as the targeting key on the transaction context:

```java
String language = ctx.getUriInfo().getQueryParameters().getFirst("language");
String userId = ctx.getUriInfo().getQueryParameters().getFirst("userId");
Map<String, Value> attrs = language != null ? Map.of("language", new Value(language)) : Map.of();
ImmutableContext c = userId != null ? new ImmutableContext(userId, attrs) : new ImmutableContext(attrs);
OpenFeatureAPI.getInstance().setTransactionContext(c);
```

The flag in `flags.json` starts at 0% on:

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

The rollout itself is just edits to that one file with the app still running:

1. Start at `[["off", 100], ["on", 0]]`. New code is shipped, zero traffic on it. Latency and 5xx panels in Grafana stay flat.
2. Bump to `[["off", 90], ["on", 10]]`. About one in ten requests now takes the slow path. The p95 panel ticks up, and you should see a small uptick on the 5xx rate panel — that is the 10% failure of the 10% rollout, so roughly 1% overall.
3. Bump to `[["off", 50], ["on", 50]]` if the dashboards still look survivable. Errors and latency double.
4. Roll back to `[["off", 100], ["on", 0]]` the moment a panel goes red. No redeploy, no restart, no Slack thread waiting on a release engineer — just save the file and the next evaluation picks it up.

I drive the demo with `requests.http`, hitting `/?userId=user-1`, `user-2`, … so the fractional buckets are stable and the dashboards tell a believable story. The whole point is that the operational lever lives outside the deploy pipeline.
