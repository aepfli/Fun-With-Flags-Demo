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
