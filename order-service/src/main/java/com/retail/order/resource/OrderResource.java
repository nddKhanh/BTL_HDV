package com.retail.order.resource;

import com.retail.order.dto.CheckoutRequest;
import com.retail.order.dto.CheckoutOutcome;
import com.retail.order.dto.CheckoutResponse;
import com.retail.order.entity.Order;
import com.retail.order.exception.OrderApiException;
import com.retail.order.service.CancelService;
import com.retail.order.service.CheckoutService;
import com.retail.order.service.OrderPersistenceService;
import com.retail.order.service.OrderResponseMapper;
import com.retail.order.util.HashUtil;
import com.retail.order.util.RequestCanonicalizer;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

@Path("/api/orders")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {
    @Inject CheckoutService checkoutService;
    @Inject CancelService cancelService;
    @Inject OrderPersistenceService persistence;
    @Inject OrderResponseMapper mapper;
    @Inject RequestCanonicalizer canonicalizer;

    @POST
    public Response checkout(@HeaderParam("X-Checkout-Key") String checkoutKey, @Valid CheckoutRequest request) {
        CheckoutOutcome outcome = checkoutService.checkout(checkoutKey, request);
        return Response.status(outcome.httpStatus()).entity(outcome.body()).build();
    }

    @GET
    @Path("/{id}")
    public CheckoutResponse getOrder(@PathParam("id") UUID id, @HeaderParam("X-Checkout-Key") String checkoutKey) {
        UUID parsedKey = canonicalizer.parseCheckoutKey(checkoutKey);
        Order order = persistence.findById(id).orElseThrow(() -> new OrderApiException(404, "ORDER_NOT_FOUND", "Order not found"));
        String suppliedHash = HashUtil.sha256Hex(parsedKey.toString());
        if (!HashUtil.constantTimeEquals(order.checkoutKeyHash == null ? null : order.checkoutKeyHash.trim(), suppliedHash)) {
            throw new OrderApiException(403, "INVALID_CHECKOUT_KEY", "Checkout key does not belong to this order");
        }
        return mapper.toResponse(order);
    }

    @POST
    @Path("/{id}/cancel")
    public Response cancel(@PathParam("id") UUID id, @HeaderParam("X-Checkout-Key") String checkoutKey) {
        var outcome = cancelService.cancel(id, checkoutKey);
        return Response.status(outcome.httpStatus()).entity(outcome.body()).build();
    }
}
