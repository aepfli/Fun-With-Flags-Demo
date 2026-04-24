package dev.openfeature.demo.quarkus;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that talks to a flagd container directly via an OpenFeature
 * client. Deliberately avoids {@code @QuarkusTest} so the app's
 * {@link OpenFeatureStartup} {@code @PostConstruct} — which wires a FILE-mode
 * provider — never runs and therefore cannot race with this test's RPC-mode
 * provider override. Mirrors the pattern in {@code go-chi/integration_test.go}.
 */
@Testcontainers
class FlagdIntegrationTest {

    @Container
    static final GenericContainer<?> FLAGD = new GenericContainer<>("ghcr.io/open-feature/flagd:latest")
            .withExposedPorts(8013)
            .withCopyFileToContainer(MountableFile.forHostPath("./flags.json"), "/flags.json")
            .withCommand("start", "--uri", "file:/flags.json")
            .waitingFor(Wait.forListeningPort());

    private Client openFeatureClient() {
        FlagdProvider provider = new FlagdProvider(FlagdOptions.builder()
                .resolverType(Config.Resolver.RPC)
                .host(FLAGD.getHost())
                .port(FLAGD.getMappedPort(8013))
                .build());
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProviderAndWait(provider);
        return api.getClient();
    }

    @Test
    void defaultGreeting() {
        // quarkusVersion is not set (no Quarkus boot), so the sem_ver branch is
        // falsy; language is also unset, so targeting falls through to the
        // default variant -> "Hello World!".
        FlagEvaluationDetails<String> details =
                openFeatureClient().getStringDetails("greetings", "Hello World");
        assertThat(details.getValue()).isEqualTo("Hello World!");
    }

    @Test
    void germanGreeting() {
        Map<String, Value> attributes = Map.of("language", new Value("de"));
        FlagEvaluationDetails<String> details = openFeatureClient()
                .getStringDetails("greetings", "Hello World", new ImmutableContext(attributes));
        assertThat(details.getValue()).isEqualTo("Hallo Welt!");
    }
}
