package com.retail.order.exception;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logmanager.MDC;

import java.io.IOException;
import java.util.UUID;

@Provider
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {
    @Inject RequestContextData requestContext;

    @Override
    public void filter(ContainerRequestContext request) throws IOException {
        String id = request.getHeaderString("X-Request-Id");
        if (id == null || !id.matches("[A-Za-z0-9._-]{1,100}")) {
            id = UUID.randomUUID().toString();
        }
        requestContext.setRequestId(id);
        MDC.put("requestId", id);
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {
        String id = requestContext.getRequestId();
        if (id != null) {
            response.getHeaders().putSingle("X-Request-Id", id);
        }
        MDC.remove("requestId");
    }
}
