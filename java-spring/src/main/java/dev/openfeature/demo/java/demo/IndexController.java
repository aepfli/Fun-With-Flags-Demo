package dev.openfeature.demo.java.demo;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IndexController {

    @GetMapping("/")
    public FlagEvaluationDetails<String> helloWorld() {
        Client client = OpenFeatureAPI.getInstance().getClient();
        return client.getStringDetails("greetings", "No World");
    }

    @GetMapping("/error")
    public FlagEvaluationDetails<Boolean> error() {
        Client client = OpenFeatureAPI.getInstance().getClient();
        return client.getBooleanDetails("greetings", false);
    }
}
