package com.retail.catalog.dto;

import java.util.List;
import java.util.UUID;

/**
 * Contract C5 — GET /internal/ai/products. Knowledge only: never price, stock,
 * customer or order data.
 */
public final class KnowledgeFeedDto {

    private KnowledgeFeedDto() {
    }

    public record ProductKnowledgeDto(UUID id, String sku, String name, String description, String categoryName) {
    }

    /** nextOffset is null when there is no further page. */
    public record KnowledgeFeedResponse(List<ProductKnowledgeDto> items, Integer nextOffset) {
    }
}
