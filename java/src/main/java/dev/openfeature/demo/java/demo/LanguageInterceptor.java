package dev.openfeature.demo.java.demo;

import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ThreadLocalTransactionContextPropagator;
import dev.openfeature.sdk.Value;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;

public class LanguageInterceptor implements HandlerInterceptor {
    public LanguageInterceptor() {
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String language = request.getParameter("language");
        if (language != null) {
            HashMap<String, Value> attributes = new HashMap<>();
            attributes.put("language", new Value(language));
            ImmutableContext evaluationContext = new ImmutableContext(attributes);
            OpenFeatureAPI.getInstance().setTransactionContext(evaluationContext);
        }
        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        OpenFeatureAPI.getInstance().setTransactionContext(new ImmutableContext());
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

    static {
        OpenFeatureAPI.getInstance().setTransactionContextPropagator(new ThreadLocalTransactionContextPropagator());
    }
}
