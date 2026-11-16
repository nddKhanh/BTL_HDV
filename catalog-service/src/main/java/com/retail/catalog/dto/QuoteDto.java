package com.retail.catalog.dto;

import java.util.List;
import java.util.UUID;

/** Contract C1 — POST /internal/catalog/quote. Field names are frozen by the shared contract. */
public final class QuoteDto {

    private QuoteDto() {
    }

    public record QuoteItemRequest(UUID productId, Integer quantity) {
    }

    public record QuoteRequest(List<QuoteItemRequest> items) {
    }

    public record QuoteLineResponse(
            UUID productId,
            String sku,
            String name,
            int quantity,
            long unitPriceVnd,
            long lineTotalVnd) {
    }

    public record QuoteResponse(String currency, List<QuoteLineResponse> items, long totalVnd) {
    }
}
