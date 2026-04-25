package dev.openfeature.demo.quarkus;

import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class LanguageRequestFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext ctx) {
        String language = ctx.getUriInfo().getQueryParameters().getFirst("language");
        String userId = ctx.getUriInfo().getQueryParameters().getFirst("userId");
        Map<String, Value> attrs = language != null
                ? Map.of("language", new Value(language))
                : Map.of();
        ImmutableContext c = userId != null
                ? new ImmutableContext(userId, attrs)
                : new ImmutableContext(attrs);
        OpenFeatureAPI.getInstance().setTransactionContext(c);
    }
}
