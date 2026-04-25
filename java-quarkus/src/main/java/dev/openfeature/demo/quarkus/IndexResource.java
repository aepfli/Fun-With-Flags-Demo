package dev.openfeature.demo.quarkus;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.concurrent.ThreadLocalRandom;

@Path("/")
public class IndexResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response index() {
        Client client = OpenFeatureAPI.getInstance().getClient();
        boolean newAlgo = client.getBooleanValue("new_greeting_algo", false);
        if (newAlgo) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (ThreadLocalRandom.current().nextDouble() < 0.1) {
                return Response.status(500).entity("simulated failure in new_greeting_algo").build();
            }
        }
        return Response.ok(client.getStringDetails("greetings", "No World")).build();
    }
}
