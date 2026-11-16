package com.retail.catalog.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * /api/admin/** requires X-Admin-Key, /internal/** requires X-Internal-Key.
 * Runs pre-matching so unknown protected paths are also rejected before routing.
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION)
public class ApiKeyFilter implements ContainerRequestFilter {

    public static final String ADMIN_HEADER = "X-Admin-Key";
    public static final String INTERNAL_HEADER = "X-Internal-Key";

    @Inject
    ApiKeys apiKeys;

    @Override
    public void filter(ContainerRequestContext request) {
        String path = normalize(request.getUriInfo().getPath());
        if (isUnder(path, "api/admin")) {
            if (!apiKeys.isValidAdminKey(request.getHeaderString(ADMIN_HEADER))) {
                reject(request);
            }
        } else if (isUnder(path, "internal")) {
            if (!apiKeys.isValidInternalKey(request.getHeaderString(INTERNAL_HEADER))) {
                reject(request);
            }
        }
    }

    private static String normalize(String path) {
        String p = path == null ? "" : path;
        while (p.startsWith("/")) p = p.substring(1);
        return p;
    }

    private static boolean isUnder(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static void reject(ContainerRequestContext request) {
        Object requestId = request.getProperty(RequestIdFilter.REQUEST_ID_PROP);
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "UNAUTHORIZED");
        error.put("message", "Missing or invalid API key");
        error.put("requestId", requestId == null ? "unknown" : requestId.toString());
        request.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", error))
                .build());
    }
}
