package dev.openfeature.demo.java.demo;

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
 * Integration test that spins up a real flagd container with testcontainers and evaluates flags
 * against it via a fresh OpenFeature client. No Spring context is booted, which keeps this test
 * independent of {@link OpenFeatureConfig}'s {@code @PostConstruct} wiring and makes it easy to
 * reason about which targeting rules fire.
 *
 * <p>Mirrors the shape of {@code go-chi/integration_test.go}: testcontainers owns the flagd
 * lifecycle, an OpenFeature client is pointed at its mapped port, and the expected variant is
 * asserted for the default and German cases.
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
        // No springVersion and no language context is set here (Spring isn't booted), so the
        // first targeting rule (sem_ver on springVersion) evaluates false, the language check
        // evaluates false, and flagd falls back to the default variant "hello".
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
