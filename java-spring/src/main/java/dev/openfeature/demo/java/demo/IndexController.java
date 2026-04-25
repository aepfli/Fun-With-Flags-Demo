package dev.openfeature.demo.java.demo;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ThreadLocalRandom;

@RestController
public class IndexController {

    @GetMapping("/")
    public ResponseEntity<?> helloWorld() {
        Client client = OpenFeatureAPI.getInstance().getClient();
        boolean newAlgo = client.getBooleanValue("new_greeting_algo", false);
        if (newAlgo) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (ThreadLocalRandom.current().nextDouble() < 0.1) {
                return ResponseEntity.status(500).body("simulated failure in new_greeting_algo");
            }
        }
        return ResponseEntity.ok(client.getStringDetails("greetings", "No World"));
    }

    @GetMapping("/error")
    public FlagEvaluationDetails<Boolean> error() {
        Client client = OpenFeatureAPI.getInstance().getClient();
        return client.getBooleanDetails("greetings", false);
    }
}
