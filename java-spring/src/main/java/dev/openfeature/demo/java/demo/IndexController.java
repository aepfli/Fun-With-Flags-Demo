package dev.openfeature.demo.java.demo;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;

@RestController
public class IndexController {

    @GetMapping("/")
    public FlagEvaluationDetails<String> helloWorld(@RequestParam(required = false) String language) {
        Client client = OpenFeatureAPI.getInstance().getClient();
        HashMap<String, Value> attributes = new HashMap<>();
        if (language != null) {
            attributes.put("language", new Value(language));
        }
        ImmutableContext evaluationContext = new ImmutableContext(attributes);
        return client.getStringDetails("greetings", "No World", evaluationContext);
    }
}
