package com.retail.catalog.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.retail.catalog.security.RequestIdHolder;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps every error to the shared contract envelope:
 * {"error":{"code":"...","message":"...","requestId":"..."}}. Never returns stack traces.
 */
public class ApiExceptionMapper {

    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class);

    @Inject
    RequestIdHolder requestIdHolder;

    @ServerExceptionMapper
    public Response mapApi(ApiException e) {
        return build(e.status, e.code, e.getMessage());
    }

    @ServerExceptionMapper
    public Response mapValidation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ApiExceptionMapper::describe)
                .sorted()
                .collect(Collectors.joining("; "));
        return build(422, "VALIDATION_ERROR", message.isBlank() ? "Invalid input" : message);
    }

    @ServerExceptionMapper
    public Response mapJson(JsonProcessingException e) {
        return build(400, "INVALID_JSON", "Malformed JSON request body");
    }

    @ServerExceptionMapper
    public Response mapWebApplication(WebApplicationException e) {
        int status = e.getResponse() == null ? 500 : e.getResponse().getStatus();
        if (status == 400 && hasJsonCause(e)) {
            return mapJson(null);
        }
        if (status >= 500) {
            LOG.errorf(e, "requestId=%s unexpected web error", requestId());
        }
        return build(status, codeFor(status), messageFor(status));
    }

    @ServerExceptionMapper
    public Response mapUnexpected(Exception e) {
        LOG.errorf(e, "requestId=%s unhandled error", requestId());
        return build(500, "INTERNAL_ERROR", "Unexpected server error");
    }

    private Response build(int status, String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("requestId", requestId());
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", error))
                .build();
    }

    private String requestId() {
        try {
            String id = requestIdHolder.value;
            return id == null ? "unknown" : id;
        } catch (RuntimeException notInRequestScope) {
            return "unknown";
        }
    }

    private static boolean hasJsonCause(Throwable e) {
        for (Throwable t = e.getCause(); t != null && t != t.getCause(); t = t.getCause()) {
            if (t instanceof JsonProcessingException) return true;
        }
        return false;
    }

    private static String describe(ConstraintViolation<?> v) {
        String path = v.getPropertyPath() == null ? "" : v.getPropertyPath().toString();
        // strip "methodName.argName." prefix produced by method validation
        int lastDot = path.lastIndexOf('.');
        String field = lastDot >= 0 ? path.substring(lastDot + 1) : path;
        return (field.isBlank() ? "" : field + ": ") + v.getMessage();
    }

    private static String codeFor(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> status >= 500 ? "INTERNAL_ERROR" : "HTTP_" + status;
        };
    }

    private static String messageFor(int status) {
        return switch (status) {
            case 400 -> "Malformed request";
            case 401 -> "Missing or invalid API key";
            case 403 -> "Forbidden";
            case 404 -> "Resource not found";
            case 405 -> "Method not allowed";
            case 406 -> "Not acceptable";
            case 415 -> "Unsupported media type";
            default -> status >= 500 ? "Unexpected server error" : "Request failed";
        };
    }
}
