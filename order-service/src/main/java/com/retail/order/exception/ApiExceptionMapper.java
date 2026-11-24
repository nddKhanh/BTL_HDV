package com.retail.order.exception;

import com.retail.order.dto.ApiError;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.UUID;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {
    @Inject RequestContextData requestContext;

    @Override
    public Response toResponse(Throwable exception) {
        int status = 500;
        String code = "INTERNAL_SERVER_ERROR";
        String message = "An unexpected error occurred";

        if (exception instanceof OrderApiException api) {
            status = api.httpStatus(); code = api.code(); message = api.getMessage();
        } else if (exception instanceof ConstraintViolationException) {
            status = 422; code = "VALIDATION_ERROR"; message = "Request fields are invalid";
        } else if (exception instanceof WebApplicationException web) {
            status = web.getResponse().getStatus();
            code = status == 404 ? "NOT_FOUND" : "BAD_REQUEST";
            message = status >= 500 ? "A dependent service failed" : "Request could not be processed";
        }

        String requestId = requestContext.getRequestId();
        if (requestId == null) requestId = UUID.randomUUID().toString();
        return Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(new ApiError(new ApiError.ErrorBody(code, message, requestId))).build();
    }
}
