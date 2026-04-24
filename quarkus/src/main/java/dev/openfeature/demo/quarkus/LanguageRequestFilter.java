package dev.openfeature.demo.quarkus;

import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.HashMap;
import java.util.List;

@Provider
public class LanguageRequestFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        List<String> languageValues = requestContext.getUriInfo()
                .getQueryParameters()
                .get("language");
        if (languageValues != null && !languageValues.isEmpty()) {
            String language = languageValues.get(0);
            HashMap<String, Value> attributes = new HashMap<>();
            attributes.put("language", new Value(language));
            OpenFeatureAPI.getInstance().setTransactionContext(new ImmutableContext(attributes));
        } else {
            OpenFeatureAPI.getInstance().setTransactionContext(new ImmutableContext());
        }
    }
}
