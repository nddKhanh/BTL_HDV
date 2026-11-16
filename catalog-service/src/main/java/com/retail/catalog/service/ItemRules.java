package com.retail.catalog.service;

import com.retail.catalog.exception.ApiException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Shared rules for C1/C2 item lists: 1–20 lines, quantity 1–99, unique productId. */
final class ItemRules {

    static final int MAX_LINES = 20;
    static final int MIN_QUANTITY = 1;
    static final int MAX_QUANTITY = 99;

    /**
     * Ascending productId order identical to PostgreSQL uuid ordering (unsigned, byte-wise),
     * which the canonical lowercase string form preserves. Used for hashing and lock ordering.
     */
    static final Comparator<UUID> PRODUCT_ID_ORDER = Comparator.comparing(UUID::toString);

    private ItemRules() {
    }

    static void requireLineCount(Collection<?> items) {
        if (items == null || items.isEmpty() || items.size() > MAX_LINES) {
            throw invalid("items must contain between 1 and " + MAX_LINES + " lines");
        }
        if (items.stream().anyMatch(i -> i == null)) {
            throw invalid("items must not contain null entries");
        }
    }

    static void requireLine(UUID productId, Integer quantity) {
        if (productId == null) {
            throw invalid("productId is required");
        }
        if (quantity == null || quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw invalid("quantity must be between " + MIN_QUANTITY + " and " + MAX_QUANTITY);
        }
    }

    static void requireUnique(List<UUID> productIds) {
        Set<UUID> seen = new HashSet<>();
        for (UUID id : productIds) {
            if (!seen.add(id)) {
                throw invalid("duplicate productId " + id);
            }
        }
    }

    static ApiException invalid(String message) {
        return ApiException.unprocessable("INVALID_ITEMS", message);
    }

    static ApiException productNotFound(UUID id) {
        return ApiException.notFound("PRODUCT_NOT_FOUND", "Product not found: " + id);
    }

    static ApiException productInactive(UUID id) {
        return ApiException.conflict("PRODUCT_INACTIVE", "Product is not active: " + id);
    }
}
