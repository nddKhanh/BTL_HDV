package com.retail.ai.dto;

import java.util.List;

// Contract C5: keep the property names exactly as in GUIDE.
public record ProductFeedResponse(List<ProductKnowledgeDto> items, Integer nextOffset) {
    public record ProductKnowledgeDto(String id, String sku, String name,
                                      String description, String categoryName) {}
}
