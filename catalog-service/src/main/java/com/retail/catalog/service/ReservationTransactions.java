package com.retail.catalog.service;

import com.retail.catalog.dto.ReservationDto.ReservationResponse;
import com.retail.catalog.dto.ReservationDto.ReserveItemRequest;
import com.retail.catalog.dto.ReservationDto.ReserveOutcome;
import com.retail.catalog.entity.Product;
import com.retail.catalog.entity.ReservationStatus;
import com.retail.catalog.entity.StockReservation;
import com.retail.catalog.entity.StockReservationItem;
import com.retail.catalog.exception.ApiException;
import com.retail.catalog.repository.ProductRepository;
import com.retail.catalog.repository.ReservationItemRepository;
import com.retail.catalog.repository.ReservationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Transaction boundaries for C2/C4. Kept in a separate bean so that InventoryService can
 * call them through the CDI proxy (and therefore get real, independent transactions).
 * No outbound HTTP is ever made while one of these transactions is open.
 */
@ApplicationScoped
public class ReservationTransactions {

    @Inject
    ReservationRepository reservations;

    @Inject
    ReservationItemRepository reservationItems;

    @Inject
    ProductRepository products;

    /**
     * C2 in ONE local transaction. {@code sortedItems} must already be validated and sorted by
     * productId ASC so that product rows are always locked in the same order (no deadlocks).
     */
    @Transactional
    public ReserveOutcome reserve(UUID orderId, String requestHash, List<ReserveItemRequest> sortedItems) {
        // Serialize concurrent calls for the same orderId; released automatically at commit/rollback.
        lockOrderId(orderId);

        var existing = reservations.findByIdForUpdate(orderId);
        if (existing.isPresent()) {
            return new ReserveOutcome(evaluateExisting(existing.get(), requestHash), false);
        }

        StockReservation reservation = new StockReservation();
        reservation.setOrderId(orderId);
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setRequestHash(requestHash);

        for (ReserveItemRequest item : sortedItems) {
            Product product = products.findByIdForUpdate(item.productId())
                    .orElseThrow(() -> ItemRules.productNotFound(item.productId()));
            if (!Boolean.TRUE.equals(product.getActive())) {
                throw ItemRules.productInactive(product.getId());
            }
            if (!product.getPriceVnd().equals(item.expectedUnitPriceVnd())) {
                throw ApiException.conflict("PRICE_CHANGED",
                        "Price changed for product " + product.getId());
            }
            if (product.getAvailableStock() < item.quantity()) {
                throw ApiException.conflict("OUT_OF_STOCK", "Insufficient available stock");
            }
            product.setAvailableStock(product.getAvailableStock() - item.quantity());

            StockReservationItem line = new StockReservationItem();
            line.setProduct(product);
            line.setQuantity(item.quantity());
            line.setUnitPriceVnd(product.getPriceVnd());
            reservation.addItem(line);
        }

        reservations.persist(reservation);
        reservations.flush(); // surface a PK (orderId) unique_violation inside this transaction
        return new ReserveOutcome(response(reservation), true);
    }

    /** Fallback after a lost insert race: read the winner's result in a brand-new transaction. */
    @Transactional(TxType.REQUIRES_NEW)
    public ReserveOutcome readAfterConflict(UUID orderId, String requestHash) {
        StockReservation reservation = reservations.findByIdOptional(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "Reservation insert conflicted but no row exists for " + orderId));
        return new ReserveOutcome(evaluateExisting(reservation, requestHash), false);
    }

    /** C4: idempotent release; stock is restored exactly once. The row is never deleted. */
    @Transactional
    public ReservationResponse release(UUID orderId) {
        StockReservation reservation = reservations.findByIdForUpdate(orderId)
                .orElseThrow(InventoryService::reservationNotFound);
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return response(reservation);
        }

        List<StockReservationItem> lines = new ArrayList<>(reservationItems.findByOrderIdSorted(orderId));
        lines.sort(Comparator.comparing((StockReservationItem l) -> l.getId().getProductId(),
                ItemRules.PRODUCT_ID_ORDER));
        for (StockReservationItem line : lines) {
            UUID productId = line.getId().getProductId();
            Product product = products.findByIdForUpdate(productId)
                    .orElseThrow(() -> new IllegalStateException("Reserved product missing: " + productId));
            product.setAvailableStock(Math.addExact(product.getAvailableStock(), line.getQuantity()));
        }
        reservation.setStatus(ReservationStatus.RELEASED);
        reservations.flush();
        return response(reservation);
    }

    private static ReservationResponse evaluateExisting(StockReservation reservation, String requestHash) {
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            throw ApiException.conflict("RESERVATION_RELEASED", "Reservation was already released");
        }
        if (!reservation.getRequestHash().trim().equals(requestHash)) {
            throw ApiException.conflict("RESERVATION_CONFLICT",
                    "orderId already reserved with a different payload");
        }
        return response(reservation);
    }

    private void lockOrderId(UUID orderId) {
        long key = orderId.getMostSignificantBits() ^ orderId.getLeastSignificantBits();
        products.getEntityManager()
                .createNativeQuery("select 1 from (select pg_advisory_xact_lock(:key)) l")
                .setParameter("key", key)
                .getSingleResult();
    }

    private static ReservationResponse response(StockReservation r) {
        return new ReservationResponse(r.getOrderId(), r.getStatus());
    }
}
