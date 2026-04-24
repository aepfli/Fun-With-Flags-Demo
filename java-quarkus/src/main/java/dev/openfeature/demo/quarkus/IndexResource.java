package dev.openfeature.demo.quarkus;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.HashMap;

@Path("/")
public class IndexResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public FlagEvaluationDetails<String> helloWorld(@QueryParam("language") String language) {
        Client client = OpenFeatureAPI.getInstance().getClient();
        HashMap<String, Value> attributes = new HashMap<>();
        if (language != null && !language.isEmpty()) {
            attributes.put("language", new Value(language));
        }
        return client.getStringDetails("greetings", "No World", new ImmutableContext(attributes));
    }
}
