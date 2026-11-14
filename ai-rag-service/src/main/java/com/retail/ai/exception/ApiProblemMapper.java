package com.retail.ai.exception;

import com.retail.ai.security.RequestIdHolder;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public class ApiProblemMapper implements ExceptionMapper<ApiProblem> {
    @Inject RequestIdHolder request;

    @Override
    public Response toResponse(ApiProblem e) {
        String requestId = request.value == null ? "unknown" : request.value;
        return Response.status(e.status).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", Map.of("code", e.code,
                        "message", e.getMessage(), "requestId", requestId)))
                .build();
    }
}
