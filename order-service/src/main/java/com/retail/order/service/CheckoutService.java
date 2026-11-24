package com.retail.order.service;

import com.retail.order.dto.CheckoutOutcome;
import com.retail.order.dto.CheckoutRequest;
import com.retail.order.dto.CheckoutResponse;
import com.retail.order.dto.QuoteDtos;
import com.retail.order.dto.ReservationDtos;
import com.retail.order.entity.Order;
import com.retail.order.entity.OrderStatus;
import com.retail.order.exception.CatalogBusinessException;
import com.retail.order.exception.CatalogUnavailableException;
import com.retail.order.exception.OrderApiException;
import com.retail.order.util.HashUtil;
import com.retail.order.util.RequestCanonicalizer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import org.jboss.logging.Logger;
import java.util.UUID;

@ApplicationScoped
public class CheckoutService {
    private static final Logger LOG = Logger.getLogger(CheckoutService.class);
    @Inject RequestCanonicalizer canonicalizer;
    @Inject OrderPersistenceService persistence;
    @Inject CatalogGateway catalog;
    @Inject OrderResponseMapper responseMapper;
    @Inject OrderRecoveryService recovery;

    public CheckoutOutcome checkout(String rawKey, CheckoutRequest rawRequest) {
        UUID checkoutKey = canonicalizer.parseCheckoutKey(rawKey);
        CheckoutRequest request = canonicalizer.normalizeAndValidate(rawRequest);
        String checkoutHash = HashUtil.sha256Hex(checkoutKey.toString());
        String requestHash = canonicalizer.requestHash(request);

        var existing = persistence.findByCheckoutKeyHash(checkoutHash);
        if (existing.isPresent()) {
            Order old = existing.get();
            ensureSameRequest(old, requestHash);
            if (old.status == OrderStatus.PENDING) recovery.tryRecoverOne(old.id);
            Order refreshed = persistence.findById(old.id).orElseThrow();
            return new CheckoutOutcome(responseMapper.toResponse(refreshed),
                    refreshed.status == OrderStatus.PENDING ? 202 : 200);
        }

        // C1 quote must finish before creating a local PENDING order.
        QuoteDtos.Response quote;
        try {
            quote = catalog.quote(new QuoteDtos.Request(request.items().stream()
                    .map(item -> new QuoteDtos.ItemRequest(item.productId(), item.quantity())).toList()));
            validateQuote(request, quote);
        } catch (CatalogBusinessException ex) {
            throw new OrderApiException(ex.status(), ex.code(), ex.getMessage());
        } catch (CatalogUnavailableException ex) {
            throw new OrderApiException(503, "CATALOG_UNAVAILABLE", "Catalog service is unavailable; retry with the same checkout key");
        }

        UUID orderId = UUID.randomUUID();
        Order created;
        try {
            persistence.createPending(orderId, checkoutHash, requestHash, request, quote);
            created = persistence.findById(orderId).orElseThrow();
        } catch (PersistenceException ex) {
            // A simultaneous request may have won the unique checkout-key constraint.
            var raced = persistence.findByCheckoutKeyHash(checkoutHash);
            if (raced.isPresent()) {
                ensureSameRequest(raced.get(), requestHash);
                Order current = persistence.findById(raced.get().id).orElseThrow();
                return new CheckoutOutcome(responseMapper.toResponse(current), current.status == OrderStatus.PENDING ? 202 : 200);
            }
            LOG.error("Could not persist pending order", ex);
            throw new OrderApiException(503, "ORDER_PERSISTENCE_FAILED", "Order could not be saved; retry with the same checkout key");
        }

        try {
            ReservationDtos.Response reservation = catalog.reserve(reservationRequest(created));
            if (!"ACTIVE".equalsIgnoreCase(reservation.status())) {
                throw new CatalogUnavailableException("Catalog has not confirmed the reservation");
            }
            persistence.transition(orderId, OrderStatus.PENDING, OrderStatus.CONFIRMED, null);
        } catch (CatalogBusinessException ex) {
            persistence.transition(orderId, OrderStatus.PENDING, OrderStatus.REJECTED, ex.code());
        } catch (CatalogUnavailableException ex) {
            // Never reject on timeout: Catalog may already have decreased stock.
            LOG.warnf("Order %s remains PENDING because Catalog outcome is uncertain", orderId);
        }

        Order result = persistence.findById(orderId).orElseThrow();
        int status = result.status == OrderStatus.PENDING ? 202 : 201;
        return new CheckoutOutcome(responseMapper.toResponse(result), status);
    }

    private void ensureSameRequest(Order existing, String requestHash) {
        if (!HashUtil.constantTimeEquals(existing.requestHash == null ? null : existing.requestHash.trim(), requestHash)) {
            throw new OrderApiException(409, "CHECKOUT_KEY_REUSED", "This checkout key was already used with a different request");
        }
    }

    private void validateQuote(CheckoutRequest request, QuoteDtos.Response quote) {
        if (quote.items().size() != request.items().size()) throw new CatalogUnavailableException("Catalog quote item count does not match request");
        if (!"VND".equalsIgnoreCase(quote.currency())) throw new CatalogUnavailableException("Catalog quote currency is not VND");
        var requested = request.items().stream().collect(java.util.stream.Collectors.toMap(
                CheckoutRequest.Item::productId, CheckoutRequest.Item::quantity));
        var quotedIds = new java.util.HashSet<UUID>();
        long sum = 0;
        for (QuoteDtos.Item item : quote.items()) {
            if (item == null || item.productId() == null || item.name() == null || item.name().isBlank()
                    || item.name().length() > 200 || item.quantity() <= 0 || item.unitPriceVnd() < 0 || item.lineTotalVnd() < 0
                    || !quotedIds.add(item.productId())
                    || !java.util.Objects.equals(requested.get(item.productId()), item.quantity())) {
                throw new CatalogUnavailableException("Catalog quote response is inconsistent");
            }
            try {
                if (Math.multiplyExact(item.unitPriceVnd(), item.quantity()) != item.lineTotalVnd())
                    throw new CatalogUnavailableException("Catalog quote line total is inconsistent");
                sum = Math.addExact(sum, item.lineTotalVnd());
            } catch (ArithmeticException ex) {
                throw new CatalogUnavailableException("Catalog quote total overflowed", ex);
            }
        }
        if (sum != quote.totalVnd() || !quotedIds.equals(requested.keySet())) throw new CatalogUnavailableException("Catalog quote total is inconsistent");
    }

    static ReservationDtos.Request reservationRequest(Order order) {
        return new ReservationDtos.Request(order.id, order.items.stream()
                .map(item -> new ReservationDtos.Item(item.id.productId, item.quantity, item.unitPriceVnd)).toList());
    }
}
