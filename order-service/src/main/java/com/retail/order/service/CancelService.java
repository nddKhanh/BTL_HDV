package com.retail.order.service;

import com.retail.order.dto.CheckoutResponse;
import com.retail.order.entity.Order;
import com.retail.order.entity.OrderStatus;
import com.retail.order.exception.CatalogBusinessException;
import com.retail.order.exception.CatalogUnavailableException;
import com.retail.order.exception.OrderApiException;
import com.retail.order.util.HashUtil;
import com.retail.order.util.RequestCanonicalizer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;

@ApplicationScoped
public class CancelService {
    @Inject OrderPersistenceService persistence;
    @Inject CatalogGateway catalog;
    @Inject OrderResponseMapper mapper;
    @Inject RequestCanonicalizer canonicalizer;

    public CancelOutcome cancel(UUID id, String rawKey) {
        UUID checkoutKey = canonicalizer.parseCheckoutKey(rawKey);
        Order order = persistence.findById(id).orElseThrow(() -> new OrderApiException(404, "ORDER_NOT_FOUND", "Order not found"));
        verifyKey(order, checkoutKey.toString());

        if (order.status == OrderStatus.CANCELLED) return new CancelOutcome(mapper.toResponse(order), 200);
        if (order.status == OrderStatus.PENDING || order.status == OrderStatus.REJECTED) {
            throw new OrderApiException(409, "ORDER_NOT_CANCELLABLE", "Only CONFIRMED orders can be cancelled");
        }
        if (order.status == OrderStatus.CONFIRMED) {
            boolean changed = persistence.transition(id, OrderStatus.CONFIRMED, OrderStatus.CANCEL_PENDING, null);
            if (!changed) {
                order = persistence.findById(id).orElseThrow();
                if (order.status == OrderStatus.CANCELLED) return new CancelOutcome(mapper.toResponse(order), 200);
                if (order.status != OrderStatus.CANCEL_PENDING) throw new OrderApiException(409, "ORDER_NOT_CANCELLABLE", "Order state changed; refresh and retry");
            }
        }

        try {
            var released = catalog.release(id);
            if (!"RELEASED".equalsIgnoreCase(released.status())) return pending(id);
            persistence.transition(id, OrderStatus.CANCEL_PENDING, OrderStatus.CANCELLED, null);
            Order done = persistence.findById(id).orElseThrow();
            return new CancelOutcome(mapper.toResponse(done), 200);
        } catch (CatalogUnavailableException | CatalogBusinessException ex) {
            return pending(id);
        }
    }

    private CancelOutcome pending(UUID id) {
        Order current = persistence.findById(id).orElseThrow();
        return new CancelOutcome(mapper.toResponse(current), 202);
    }

    private void verifyKey(Order order, String rawKey) {
        String normalizedKey;
        try { normalizedKey = UUID.fromString(rawKey.trim()).toString(); }
        catch (IllegalArgumentException ex) { normalizedKey = rawKey.trim(); }
        String hash = HashUtil.sha256Hex(normalizedKey);
        if (!HashUtil.constantTimeEquals(order.checkoutKeyHash == null ? null : order.checkoutKeyHash.trim(), hash)) {
            throw new OrderApiException(403, "INVALID_CHECKOUT_KEY", "Checkout key does not belong to this order");
        }
    }

    public record CancelOutcome(CheckoutResponse body, int httpStatus) { }
}
