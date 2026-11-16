package com.retail.catalog.service;

import com.retail.catalog.dto.ReservationDto.ReservationResponse;
import com.retail.catalog.dto.ReservationDto.ReserveItemRequest;
import com.retail.catalog.dto.ReservationDto.ReserveOutcome;
import com.retail.catalog.dto.ReservationDto.ReserveRequest;
import com.retail.catalog.exception.ApiException;
import com.retail.catalog.repository.ReservationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** C2/C3/C4 orchestration: validation, canonical hashing and unique-violation recovery. */
@ApplicationScoped
public class InventoryService {

    private static final Logger LOG = Logger.getLogger(InventoryService.class);
    private static final String RESERVATION_PK = "stock_reservations_pkey";

    @Inject
    ReservationTransactions tx;

    @Inject
    ReservationRepository reservations;

    public ReserveOutcome reserve(ReserveRequest request) {
        if (request == null || request.orderId() == null) {
            throw ApiException.unprocessable("VALIDATION_ERROR", "orderId is required");
        }
        List<ReserveItemRequest> items = request.items();
        ItemRules.requireLineCount(items);
        for (ReserveItemRequest item : items) {
            ItemRules.requireLine(item.productId(), item.quantity());
            if (item.expectedUnitPriceVnd() == null || item.expectedUnitPriceVnd() < 0) {
                throw ItemRules.invalid("expectedUnitPriceVnd must be an integer >= 0");
            }
        }
        ItemRules.requireUnique(items.stream().map(ReserveItemRequest::productId).toList());

        List<ReserveItemRequest> sorted = items.stream()
                .sorted(Comparator.comparing(ReserveItemRequest::productId, ItemRules.PRODUCT_ID_ORDER))
                .toList();
        String hash = requestHash(request.orderId(), sorted);

        try {
            return tx.reserve(request.orderId(), hash, sorted);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            if (!isReservationPkViolation(e)) throw e;
            // The losing transaction was rolled back entirely (stock included); read the winner.
            LOG.infof("Concurrent reserve for orderId=%s lost the insert race; re-reading", request.orderId());
            return tx.readAfterConflict(request.orderId(), hash);
        }
    }

    public ReservationResponse get(String rawOrderId) {
        UUID orderId = parseOrderId(rawOrderId);
        return reservations.findByIdOptional(orderId)
                .map(r -> new ReservationResponse(r.getOrderId(), r.getStatus()))
                .orElseThrow(InventoryService::reservationNotFound);
    }

    public ReservationResponse release(String rawOrderId) {
        return tx.release(parseOrderId(rawOrderId));
    }

    /**
     * SHA-256 over a canonical form independent of the original JSON order:
     * orderId, then productId:quantity:expectedUnitPriceVnd for each line sorted by productId ASC.
     */
    static String requestHash(UUID orderId, List<ReserveItemRequest> sortedItems) {
        StringBuilder canonical = new StringBuilder("v1|").append(orderId);
        for (ReserveItemRequest item : sortedItems) {
            canonical.append('|').append(item.productId())
                    .append(':').append(item.quantity())
                    .append(':').append(item.expectedUnitPriceVnd());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    static ApiException reservationNotFound() {
        return ApiException.notFound("RESERVATION_NOT_FOUND", "Reservation not found");
    }

    private static UUID parseOrderId(String raw) {
        return ServiceSupport.parseIdOrNotFound(raw, "RESERVATION_NOT_FOUND", "Reservation not found");
    }

    static boolean isReservationPkViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                if (name != null && name.toLowerCase().contains(RESERVATION_PK)) {
                    return true;
                }
                if ("23505".equals(cve.getSQLState())) {
                    return true;
                }
            }
            if (t instanceof java.sql.SQLException sqlEx && "23505".equals(sqlEx.getSQLState())) {
                return true;
            }
            if (t == t.getCause()) break;
        }
        return false;
    }
}
