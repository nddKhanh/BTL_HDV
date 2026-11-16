package com.retail.catalog.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import java.util.UUID;

/**
 * Assigns X-Request-Id to every request (reuses a sane client value, otherwise generates a UUID),
 * echoes it on the response and logs method/path/status/duration. Never logs headers or secrets.
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String REQUEST_ID_PROP = "catalog.requestId";
    private static final String START_PROP = "catalog.startNanos";
    private static final Logger LOG = Logger.getLogger(RequestIdFilter.class);

    @Inject
    RequestIdHolder requestIdHolder;

    @Override
    public void filter(ContainerRequestContext request) {
        String supplied = request.getHeaderString(HEADER);
        String requestId = isAcceptable(supplied) ? supplied : UUID.randomUUID().toString();
        request.setProperty(REQUEST_ID_PROP, requestId);
        request.setProperty(START_PROP, System.nanoTime());
        requestIdHolder.value = requestId;
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object requestId = request.getProperty(REQUEST_ID_PROP);
        if (requestId != null) {
            response.getHeaders().putSingle(HEADER, requestId);
        }
        Object start = request.getProperty(START_PROP);
        long durationMs = start instanceof Long s ? (System.nanoTime() - s) / 1_000_000 : -1;
        LOG.infof("requestId=%s method=%s path=%s status=%d durationMs=%d",
                requestId, request.getMethod(), request.getUriInfo().getPath(),
                response.getStatus(), durationMs);
    }

    private static boolean isAcceptable(String value) {
        return value != null && !value.isBlank() && value.length() <= 80
                && value.chars().allMatch(c -> c > 32 && c < 127);
    }
}
