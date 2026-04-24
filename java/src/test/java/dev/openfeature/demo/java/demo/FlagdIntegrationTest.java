package dev.openfeature.demo.java.demo;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        classes = {DemoApplication.class, FlagdIntegrationTest.TestOverrideConfig.class})
class FlagdIntegrationTest {

    @Container
    static final GenericContainer<?> FLAGD = new GenericContainer<>("ghcr.io/open-feature/flagd:latest")
            .withExposedPorts(8013)
            .withCopyFileToContainer(MountableFile.forHostPath("flags.json"), "/flags.json")
            .withCommand("start", "--uri", "file:./flags.json")
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void flagdProps(DynamicPropertyRegistry registry) {
        registry.add("flagd.host", FLAGD::getHost);
        registry.add("flagd.port", () -> FLAGD.getMappedPort(8013));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void returnsHelloWorldByDefault() {
        String body = restTemplate.getForObject("/", String.class);
        assertThat(body).contains("Hello World");
    }

    @Test
    void returnsHalloWeltForGermanLanguage() {
        String body = restTemplate.getForObject("/?language=de", String.class);
        assertThat(body).contains("Hallo Welt");
    }

    /**
     * Overrides the provider wired by {@link OpenFeatureConfig} after the context has started.
     * Spring runs {@link ApplicationRunner} beans AFTER every {@code @PostConstruct}, so this
     * reliably replaces the default-port provider with one that points at the container's
     * mapped port, and pins the Spring version evaluation context so the targeting rules
     * resolve to "hello"/"hallo" instead of the "springer" branch.
     */
    @TestConfiguration
    static class TestOverrideConfig {

        @Bean
        ApplicationRunner pointProviderAtContainer(
                @org.springframework.beans.factory.annotation.Value("${flagd.host}") String host,
                @org.springframework.beans.factory.annotation.Value("${flagd.port}") int port) {
            return args -> {
                OpenFeatureAPI api = OpenFeatureAPI.getInstance();
                FlagdOptions options = FlagdOptions.builder()
                        .resolverType(Config.Resolver.RPC)
                        .host(host)
                        .port(port)
                        .build();
                api.setProviderAndWait(new FlagdProvider(options));

                HashMap<String, Value> attributes = new HashMap<>();
                attributes.put("springVersion", new Value("0.0.0"));
                api.setEvaluationContext(new ImmutableContext(attributes));
            };
        }
    }
}
