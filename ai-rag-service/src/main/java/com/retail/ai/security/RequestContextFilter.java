package com.retail.ai.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class RequestContextFilter implements ContainerRequestFilter, ContainerResponseFilter {
    public static final String REQUEST_ID = "requestId";

    @Inject RequestIdHolder requestIdHolder;
    @ConfigProperty(name = "app.admin-key") String adminKey;
    @ConfigProperty(name = "app.internal-key") String internalKey;

    @Override
    public void filter(ContainerRequestContext request) {
        String suppliedId = request.getHeaderString("X-Request-Id");
        String requestId = (suppliedId != null && suppliedId.length() <= 80 && !suppliedId.isBlank())
                ? suppliedId : UUID.randomUUID().toString();
        request.setProperty(REQUEST_ID, requestId);
        requestIdHolder.value = requestId;

        String path = request.getUriInfo().getPath();
        if (path.startsWith("api/admin/") && !sameSecret(adminKey, request.getHeaderString("X-Admin-Key"))) {
            reject(request, requestId);
        } else if (path.startsWith("internal/") && !sameSecret(internalKey, request.getHeaderString("X-Internal-Key"))) {
            reject(request, requestId);
        }
    }

    private boolean sameSecret(String expected, String actual) {
        return expected != null && !expected.isBlank() && actual != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                                         actual.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(ContainerRequestContext request, String requestId) {
        request.abortWith(Response.status(401).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", Map.of("code", "UNAUTHORIZED",
                        "message", "Missing or invalid API key", "requestId", requestId)))
                .build());
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object requestId = request.getProperty(REQUEST_ID);
        if (requestId != null) response.getHeaders().putSingle("X-Request-Id", requestId);
    }
}
