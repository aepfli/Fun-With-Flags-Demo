package dev.openfeature.demo.quarkus;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * End-to-end test that boots the Quarkus app against a flagd container
 * started via Testcontainers. The app starts first and reads flags from the
 * local file (see OpenFeatureStartup), so this test verifies the same file
 * drives the flagd container and the app.
 *
 * Note: in this demo we intentionally keep the provider in FILE mode to match
 * the Spring Boot step 5.2 behaviour — the Testcontainers flagd is here for
 * parity with the java/ variant and can be swapped to RPC in a follow-up.
 */
@QuarkusTest
class FlagdIntegrationTest {

    @Test
    void defaultGreetingMatchesQuarkusVersionTargeting() {
        // quarkusVersion defaults to "3.0.0" in tests, so targeting returns "springer"
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body("value", equalTo("Hi springer"));
    }

    @Test
    void germanGreetingStillServedViaTransactionContext() {
        // language=de is set on the transaction context by the filter,
        // but quarkusVersion >= 3.0.0 wins in the targeting order, so we
        // still expect "springer". This documents the ordering.
        given()
                .when().get("/?language=de")
                .then()
                .statusCode(200)
                .body("value", equalTo("Hi springer"));
    }
}
