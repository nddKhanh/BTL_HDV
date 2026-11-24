package com.retail.order.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CheckoutResponse(
        UUID id,
        String status,
        String currency,
        long totalVnd,
        List<Item> items,
        String lastErrorCode,
        Instant createdAt
) {
    public record Item(UUID productId, String productName, int quantity, long unitPriceVnd, long lineTotalVnd) { }
}
