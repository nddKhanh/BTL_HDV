package com.retail.catalog.dto;

import com.retail.catalog.entity.ReservationStatus;
import java.util.List;
import java.util.UUID;

/** Contracts C2/C3/C4 — /internal/stock/reservations. */
public final class ReservationDto {

    private ReservationDto() {
    }

    public record ReserveItemRequest(UUID productId, Integer quantity, Long expectedUnitPriceVnd) {
    }

    public record ReserveRequest(UUID orderId, List<ReserveItemRequest> items) {
    }

    public record ReservationResponse(UUID orderId, ReservationStatus status) {
    }

    /** created=true → 201 Created, false → 200 OK (idempotent replay). */
    public record ReserveOutcome(ReservationResponse response, boolean created) {
    }
}
