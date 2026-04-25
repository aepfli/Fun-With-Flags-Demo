# OpenFeature Spring Boot Demo

This is a little Spring Boot Demo applitcation for OpenFeature.

Follow each Step and see how OpenFeature can be used within a Spring Boot Application

Within [reqeuests.http](requests.http) you will find requests for each section to play with.

> Note: There will be a branch for each step - within the near future. Currently there is only `step/4` which is the end state

## Step 1 Basic OpenFeature Setup

Checkout the Repository and start the application.

### Step 1.1 Add OpenFeature SDK

1. Add OpenFeature SDK to the pom.xml by adding following dependencies

    ```xml
    <dependency>
        <groupId>dev.openfeature</groupId>
        <artifactId>sdk</artifactId>
        <version>1.14.2</version>
    </dependency>
    ```

2. Add Evaluation a Feature Flag Evaluation to the IndexController

    ```java
    @GetMapping("/")
    public FlagEvaluationDetails<String>  helloWorld() {
        Client client = OpenFeatureAPI.getInstance().getClient();
        return client.getStringDetails("greetings", "No World");
    }
    ```

If you run the code we will get `No World`, and this is expected.
We need to define a provider which our client is using.
Within the next step we will add this.

### Step 1.2 Provider Initialization

1. We will setup a provider within a PostConstruct configuration like
   ```java
   @Configuration
   public class OpenFeatureConfig {
   
       @PostConstruct
       public void initProvider() {
           OpenFeatureAPI api = OpenFeatureAPI.getInstance();
           api.setProviderAndWait(new InMemoryProvider(new HashMap<>()));
       }
   }
   ```
   
   > Note: Nothing will change during the execution at this stage, but with the next step, we add feature flags

2. Fill the HashMap within the InMemoryProvider with data like:
   ```java
    @PostConstruct
    public void initProvider() {
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        HashMap<String, Flag<?>> flags = new HashMap<>();
        flags.put("greetings",
                Flag.builder()
                        .variant("goodbye", "Goodbye World!")
                        .variant("hello", "Hello World!")
                        .defaultVariant("hello")
                        .build());

        api.setProviderAndWait(new InMemoryProvider(flags));
    }
   ```
   
   > Note: Yes it is tedious to do this via code, that is just the simplest example :)

   Now we can change the default variant and see OpenFeatures Basic Magic.
   Depending on the default variant we should see either `Hello World` or `Goodbye World`.

### Summary

We have now added OpenFeature to our codebase and using it to evaluate feature flags.
However, the feature flag definition is in code and does not offer us the flexibility we want.
Let's jump into the next chapter and retrieve feature flags from a file.

## Step 2 Providers

Flagd is our cloud native reference implementation and it comes with a lot of interesting features.
First lets focus on the file provider, to show you how easy it is to change the provider.

### Step 2.1 Adding Flagd File Provider

1. To utilize flagd we need to add an additional dependency -> the flagd provider
   ```xml
     <dependency>
         <groupId>dev.openfeature.contrib.providers</groupId>
         <artifactId>flagd</artifactId>
         <version>0.11.8</version>
     </dependency>
   ```
2. We need to migrate our flag configuration to a json file for the flagd file provider.
   Therefore, we create a `flags.json` within the project root with the following content:
   
   ```json
   {
    "flags": {
      "greetings": {
        "state": "ENABLED",
          "variants": {
            "hello": "Hello World!",
            "goodbye": "Goodbye World!"
          },
          "defaultVariant": "hello"
        }
      }
   }
   ```
   
3. We need to instrument the flagD provider instead of our InMemory Provider
   ```java
   @PostConstruct
   public void initProvider() {
     OpenFeatureAPI api = OpenFeatureAPI.getInstance();
     FlagdOptions flagdOptions = FlagdOptions.builder()
             .resolverType(Config.Resolver.FILE)
             .offlineFlagSourcePath("./flags.json")
             .build();

     api.setProviderAndWait(new FlagdProvider(flagdOptions));
   }
   ```
 Now we can change the file and see that based on the file we will get different values.


## Step 3 Targeting

Targeting allows us to change the evaluation outcome based on contextual data.

### Step 3.1 Dynamic Context

Targeting allows us to modify our result based on arbitrary data.

1. Lets adapt our controller endpoint to utilize a query parameter as contextual data,
   ```java
   @GetMapping("/")
   public FlagEvaluationDetails<String> helloWorld(@RequestParam(required = false) String language) {
        Client client = OpenFeatureAPI.getInstance().getClient();
        HashMap<String, Value> attributes = new HashMap<>();
        attributes.put("language", new Value(language));
        return client.getStringDetails("greetings", "Hello World",
                new ImmutableContext(attributes));
    }
   ```

2. Lets adopt our flag and add some targeting
   ```json
   {
    "flags": {
      "greetings": {
        "state": "ENABLED",
          "variants": {
            "hallo": "Hallo Welt!",
            "hello": "Hello World!",
            "goodbye": "Goodbye World!"
          },
          "defaultVariant": "hello",
          "targeting": {
            "if": [
              {
                "===": [
                  {
                    "var": "language"
                  },
                  "de"
                ]
              },
              "hallo"
              ]
          }    
        }
      }
   }
   ``` 

### Step 3.1.1 interceptor?

Adding this context population for each endpoint is a lot of effort, why not use an interceptor for this.

1. create an interceptor called `LanguageInterceptor.java`
   ```java
   public class LanguageInterceptor implements HandlerInterceptor {
       public LanguageInterceptor() {
       }
   
       @Override
       public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
           String language = request.getParameter("language");
           if (language != null) {
               HashMap<String, Value> attributes = new HashMap<>();
               attributes.put("language", new Value(language));
               ImmutableContext evaluationContext = new ImmutableContext(attributes);
               OpenFeatureAPI.getInstance().setTransactionContext(evaluationContext);
           }
           return HandlerInterceptor.super.preHandle(request, response, handler);
       }
       
       public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
           OpenFeatureAPI.getInstance().setTransactionContext(new ImmutableContext());
           HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
       }
   
       static {
           OpenFeatureAPI.getInstance().setTransactionContextPropagator(new ThreadLocalTransactionContextPropagator());
       }
   }
   ```
   
2. adapt our `OpenFeatureConfig` to add this interceptor
   ```java
   @Configuration
   public class OpenFeatureConfig implements WebMvcConfigurer {
   
       @PostConstruct
       public void initProvider() {
           OpenFeatureAPI api = OpenFeatureAPI.getInstance();
           FlagdOptions flagdOptions = FlagdOptions.builder()
                   .resolverType(Config.Resolver.FILE)
                   .offlineFlagSourcePath("./flags.json")
                   .build();
   
           api.setProviderAndWait(new FlagdProvider(flagdOptions));
       }
   
       @Override
       public void addInterceptors(InterceptorRegistry registry) {
           registry.addInterceptor(new LanguageInterceptor());
       }
   }
   ```

3. remove the context propagation from the controller. Before we started with targeting
   ```java
       @GetMapping("/")
       public FlagEvaluationDetails<String> helloWorld() {
           Client client = OpenFeatureAPI.getInstance().getClient();
           return client.getStringDetails("greetings", "No World");
       }
   ```

### Step 3.2 Global Context

As mentioned we can also set some context globally. eg. springVersion

1. We adapt our flags configuration to also match for a certain spring version like:
   ```json
   {
     "flags": {
       "greetings": {
         "state": "ENABLED",
         "variants": {
           "springer": "Hi springer",
           "hallo": "Hallo Welt!",
           "hello": "Hello World!",
           "goodbye": "Goodbye World!"
         },
         "defaultVariant": "hello",
         "targeting": {
           "if": [
             {
               "sem_ver": [
                 {
                   "var": "springVersion"
                 },
                 ">=",
                 "3.0.0"
               ]
             },
             "springer",
             {
               "===": [
                 {
                   "var": "language"
                 },
                 "de"
               ]
             },
             "hallo"
           ]
         }
       }
     }
   }
   ```

2. Adding a Context within our initialization code:
   ```java
       @PostConstruct
       public void initProvider() {
           OpenFeatureAPI api = OpenFeatureAPI.getInstance();
           FlagdOptions flagdOptions = FlagdOptions.builder()
                   .resolverType(Config.Resolver.FILE)
                   .offlineFlagSourcePath("./flags.json")
                   .build();
   
           api.setProviderAndWait(new FlagdProvider(flagdOptions));
           
           HashMap<String, Value> attributes = new HashMap<>();
           attributes.put("springVersion", new Value(SpringVersion.getVersion()));
           ImmutableContext evaluationContext = new ImmutableContext(attributes);
           api.setEvaluationContext(evaluationContext);
       }
   ```
   
   If you change now the targeting, you will see that his version is actively affecting our evaluation.

Voila, we now see a different output as our version is one of our first arguments.

## Step 4 Hooks

Hooks allow us to enhance our code during feature flag evaluations, without writing our own provider.

### Step 4.1 creating and adding a hook

1. Creating a `CustomHook.java`
   ```java
    public class CustomHook implements Hook {
       private static final Logger LOG = LoggerFactory.getLogger(CustomHook.class);
   
   
       @Override
       public Optional<EvaluationContext> before(HookContext ctx, Map hints) {
           LOG.info("Before hook");
           return Optional.empty();
       }
   
       @Override
       public void after(HookContext ctx, FlagEvaluationDetails details, Map hints) {
           LOG.info("After hook - {}", details.getReason());
       }
   
       @Override
       public void error(HookContext ctx, Exception error, Map hints) {
           LOG.error("Error hook", error);
       }
   
       @Override
       public void finallyAfter(HookContext ctx, FlagEvaluationDetails details, Map hints) {
           LOG.info("Finally After hook - {}", details.getReason());
       }
   }
   ```

2. Adding the hook during instrumentation
   ```java
    @PostConstruct
    public void initProvider() {
        // ...
        api.addHooks(new CustomHook());
    }
   ```
   
   Take a look at the console, and see what kind of information you are getting.

## Step 5 Remote flagd

We already showed the file mode, which is good for getting a glimpse of the functionality, but flagd is more powerful.
So let's use flagd as a standalone process to fetch feature flag configurations.

### Step 5.1 Setup Flagd Standalone

1. We need a docker compose file (./docker-compose.yaml) , exposing the ports, and utilizing the same file
   ```yaml
   services:
     flagd:
       stdin_open: true
       tty: true
       container_name: flagd
       image: ghcr.io/open-feature/flagd:latest
       ports:
         - "8013:8013"
         - "8014:8014"
         - "8015:8015"
         - "8016:8016"
       env_file:
         - .env.local
       volumes:
         - "./flags.json:/flags.json"
       command: start --uri file:./flags.json
   ```

2. We can start the docker container with `docker compose up` within a terminal.
3. Let's change the flagd provider mode to either `RPC` or `IN_PROCESS`
   ```java
   FlagdOptions flagdOptions = FlagdOptions.builder()
          .resolverType(Config.Resolver.RPC)
          .offlineFlagSourcePath("./flags.json")
          .build();
   ```

There are two different behaviours we can observe depending on the mode
- RPC: everytime we evaluate a flag, we will query flagd for the evaluation.
- IN_PROCESS: we will be fetching the flag configuration, and only if there is an update to the flags.json, we will get a change event.

## Step 6 OpenTelemetry

Observability is one of the superpowers OpenFeature unlocks: every flag evaluation can be emitted as a distributed-tracing span AND as a metric, neatly correlated with the HTTP request that triggered it. With two small hooks the workshop audience sees the exact flag key, variant, and reason inside Grafana — no bespoke logging, no custom correlation id dance.

For this step we use the upstream `otel` contrib hooks (`TracesHook` + `MetricsHook`) together with the OpenTelemetry SDK autoconfigure module, which builds an `OpenTelemetry` instance exporting both signals via OTLP/gRPC. The shared LGTM (Loki/Grafana/Tempo/Mimir) stack from [`../observability`](../observability) accepts OTLP on port `4317` and exposes Grafana on port `3000`.

### Step 6.1 Add dependencies

Import the OpenTelemetry BOMs and add the contrib hook plus the SDK/exporter. Pinning via BOM keeps every OTel module on a single, consistent version.

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.opentelemetry</groupId>
      <artifactId>opentelemetry-bom</artifactId>
      <version>1.48.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
    <dependency>
      <groupId>io.opentelemetry.instrumentation</groupId>
      <artifactId>opentelemetry-instrumentation-bom</artifactId>
      <version>2.14.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>dev.openfeature.contrib.hooks</groupId>
    <artifactId>otel</artifactId>
    <version>3.2.1</version>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-extension-autoconfigure</artifactId>
  </dependency>
</dependencies>
```

> Note: At the time of writing, the canonical `opentelemetry-spring-boot-starter` had not yet been released for Spring Boot 4. We therefore use the direct SDK + autoconfigure shape, which works identically for the demo. When the starter catches up, swap these dependencies for the starter and drop the `OpenTelemetryConfig` bean below.

### Step 6.2 Configure the OpenTelemetry SDK

Create an `OpenTelemetryConfig` that builds the global SDK using the autoconfigure module. Properties from `application.properties` flow through as system properties so the SDK picks them up. With `otel.traces.exporter=otlp` AND `otel.metrics.exporter=otlp` the autoconfigure module wires up both a `SdkTracerProvider` and a `SdkMeterProvider` (the latter using an `OtlpGrpcMetricExporter`), so the resulting `OpenTelemetry` bean exposes both `getTracerProvider()` and `getMeterProvider()`.

```java
@Configuration
public class OpenTelemetryConfig {

    @Bean
    public OpenTelemetry openTelemetry(
            @Value("${otel.service.name:fun-with-flags-java-spring}") String serviceName,
            @Value("${otel.exporter.otlp.endpoint:http://localhost:4317}") String otlpEndpoint) {
        System.setProperty("otel.service.name", serviceName);
        System.setProperty("otel.exporter.otlp.endpoint", otlpEndpoint);
        System.setProperty("otel.exporter.otlp.protocol", "grpc");
        System.setProperty("otel.traces.exporter", "otlp");
        System.setProperty("otel.metrics.exporter", "otlp");
        System.setProperty("otel.logs.exporter", "none");
        System.setProperty("otel.metric.export.interval", "10000");

        return AutoConfiguredOpenTelemetrySdk.builder()
                .setResultAsGlobal()
                .build()
                .getOpenTelemetrySdk();
    }
}
```

Point the app at the LGTM stack in `application.properties`:

```properties
otel.exporter.otlp.endpoint=http://localhost:4317
otel.exporter.otlp.protocol=grpc
otel.metrics.exporter=otlp
otel.traces.exporter=otlp
otel.logs.exporter=none
otel.service.name=fun-with-flags-java-spring
otel.metric.export.interval=10000
```

### Step 6.3 Register the OpenFeature OTel hooks

The `TracesHook` creates a span for every flag evaluation, tagged with the flag key, variant, and reason — automatically nested inside whichever span is current (the Spring HTTP request, in our case). The `MetricsHook` increments a counter per evaluation, broken down by flag key / variant / reason, so we get an aggregate view in Grafana on top of the individual traces.

`MetricsHook` needs the `OpenTelemetry` instance to grab the meter provider, so we inject the bean into `OpenFeatureConfig` via constructor injection:

```java
import dev.openfeature.contrib.hooks.otel.MetricsHook;
import dev.openfeature.contrib.hooks.otel.TracesHook;

private final io.opentelemetry.api.OpenTelemetry openTelemetry;

public OpenFeatureConfig(io.opentelemetry.api.OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
}

@PostConstruct
public void initProvider() {
    // ... existing provider setup ...
    api.addHooks(new CustomHook());
    api.addHooks(new TracesHook());
    api.addHooks(new MetricsHook(openTelemetry));
}
```

> Note: In the 3.2.1 release the classes are called `TracesHook` and `MetricsHook`. Older docs reference `OpenTelemetryHook` — same idea, split into two focused hooks.

### Step 6.4 Run it

1. Start the shared observability stack (from the repo root):
   ```bash
   cd ../observability && docker compose up -d
   ```
2. Start flagd and the Spring Boot app as in earlier steps:
   ```bash
   docker compose up -d   # starts flagd
   ./mvnw spring-boot:run
   ```
3. Hit the endpoint a few times to generate traffic:
   ```bash
   curl http://localhost:8080/
   curl 'http://localhost:8080/?language=de'
   ```
4. Open Grafana at <http://localhost:3000> (login `admin` / `admin`).
   - **Traces**: open **Explore → Tempo**, pick the `fun-with-flags-java-spring` service, and inspect a trace. You should see the HTTP request span with the flag evaluation span nested inside, carrying `feature_flag.key`, variant, and reason attributes.
   - **Metrics**: open the **Fun With Flags — Feature Flag Metrics** dashboard to see flag-evaluation counters per key / variant / reason.

> Heads up: the metric export interval is 10 seconds (`otel.metric.export.interval=10000`), so it takes 10–15 seconds after the first evaluation before the dashboard lights up. Traces show up immediately.

## Step 7 Progressive Rollout

Here is the part where feature flags pay for themselves. So far we have wired everything up, watched our flag evaluations show up in Grafana, and changed strings in a config file. Cute, but not the real value. The real value is that **deploying** code and **releasing** code become two separate things. We can ship risky code dark, turn it on for 1% of users, watch the dashboards, and turn it off again — without rebuilding, redeploying, or paging anyone.

To make that tangible we are going to ship a deliberately bad new "greeting algorithm": it sleeps 200ms (so latency goes up) and 10% of the time it returns HTTP 500 (so error rate goes up). In real life this would be the new pricing engine, the rewritten recommendation service, the migration off your legacy database — anything where you want to be able to bail out fast.

### Step 7.1 The flag

We add a boolean flag `new_greeting_algo` with **fractional** targeting. Default is 100% off:

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

`fractional` buckets users by `targetingKey` by default — same key, same bucket, every time. That is what makes a rollout *sticky*: if I give you the new code path on the first request, you get it on the next request too. Bumping the percentages from `[100, 0]` to `[90, 10]` to `[50, 50]` is how we ramp up. **No code change, no redeploy.**

### Step 7.2 Reading the flag

In `IndexController` we read the flag and inject the bad behaviour when it is on:

```java
@GetMapping("/")
public ResponseEntity<?> helloWorld() {
    Client client = OpenFeatureAPI.getInstance().getClient();
    boolean newAlgo = client.getBooleanValue("new_greeting_algo", false);
    if (newAlgo) {
        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
            return ResponseEntity.status(500).body("simulated failure in new_greeting_algo");
        }
    }
    return ResponseEntity.ok(client.getStringDetails("greetings", "No World"));
}
```

### Step 7.3 Stable bucketing via targetingKey

For the fractional split to be sticky we need a stable identifier per caller. We extend the interceptor to read a `userId` query param and pass it as the OpenFeature **targetingKey**:

```java
String language = request.getParameter("language");
String userId = request.getParameter("userId");
HashMap<String, Value> attributes = new HashMap<>();
if (language != null) attributes.put("language", new Value(language));
ImmutableContext ctx = userId != null
    ? new ImmutableContext(userId, attributes)
    : new ImmutableContext(attributes);
OpenFeatureAPI.getInstance().setTransactionContext(ctx);
```

`new ImmutableContext(targetingKey, attributes)` is the constructor in SDK 1.14.2 that lets you set the targeting key and attributes in one go.

### Step 7.4 Run the rollout

1. Start the observability stack and flagd if they are not already running:
   ```bash
   cd ../observability && docker compose up -d
   cd -
   docker compose up -d   # flagd
   ```
2. Start the app:
   ```bash
   ./mvnw spring-boot:run
   ```
3. Turn on the loadgen by flipping `loadgen_active` to true (see the `loadgen` section of the workshop). This drives steady traffic so the Grafana panels actually have something to draw.
4. Open Grafana at <http://localhost:3000> and pin the **HTTP request latency (p50, p99)** and **HTTP 5xx per second** panels.
5. Edit `flags.json` and ramp the rollout — flagd watches the file and picks up changes within a second:
   - `[90, 10]` — 10% of users on the new algo. p99 latency starts to lift, you start to see the occasional 5xx.
   - `[50, 50]` — 50/50. p50 jumps up by ~200ms, 5xx rate is now obvious.
   - `[100, 0]` — back to safe. Latency and errors return to baseline within seconds.

That last step is the one to internalise. **No deploy. No rebuild. No restart.** Just edit the flag, watch the dashboard recover. That is what "decouple deployment from release" means in practice — and it is the same lever you would pull at 3am when the new pricing engine starts erroring in production.
