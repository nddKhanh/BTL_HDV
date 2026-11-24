package com.retail.order.util;

import com.retail.order.dto.CheckoutRequest;
import com.retail.order.exception.OrderApiException;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class RequestCanonicalizer {
    private static final java.util.regex.Pattern UUID_PATTERN = java.util.regex.Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public CheckoutRequest normalizeAndValidate(CheckoutRequest input) {
        if (input == null || input.customer() == null || input.items() == null) {
            throw new OrderApiException(422, "INVALID_CHECKOUT", "Customer and items are required");
        }

        String name = trim(input.customer().name());
        String phone = trim(input.customer().phone());
        String address = trim(input.customer().address());

        if (name.isEmpty() || name.length() > 120
                || phone.isEmpty() || phone.length() > 20
                || address.isEmpty() || address.length() > 500) {
            throw new OrderApiException(422, "INVALID_CHECKOUT", "Customer fields are invalid");
        }
        if (input.items().isEmpty() || input.items().size() > 20) {
            throw new OrderApiException(422, "INVALID_ITEMS", "Checkout must contain 1 to 20 item rows");
        }

        Set<UUID> seen = new HashSet<>();
        List<CheckoutRequest.Item> items = new ArrayList<>();
        for (CheckoutRequest.Item item : input.items()) {
            if (item == null || item.productId() == null || item.quantity() < 1 || item.quantity() > 99) {
                throw new OrderApiException(422, "INVALID_ITEMS",
                        "Each item requires a productId and quantity from 1 to 99");
            }
            if (!seen.add(item.productId())) {
                throw new OrderApiException(422, "INVALID_ITEMS", "Duplicate productId is not allowed");
            }
            items.add(item);
        }
        items.sort(Comparator.comparing(item -> item.productId().toString()));
        return new CheckoutRequest(new CheckoutRequest.Customer(name, phone, address), List.copyOf(items));
    }

    public String requestHash(CheckoutRequest request) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, request.customer().name());
        append(canonical, request.customer().phone());
        append(canonical, request.customer().address());
        for (CheckoutRequest.Item item : request.items()) {
            append(canonical, item.productId().toString().toLowerCase());
            append(canonical, Integer.toString(item.quantity()));
        }
        return HashUtil.sha256Hex(canonical.toString());
    }

    public UUID parseCheckoutKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new OrderApiException(400, "CHECKOUT_KEY_REQUIRED", "X-Checkout-Key header is required");
        }
        String value = raw.trim();
        if (!UUID_PATTERN.matcher(value).matches()) {
            throw new OrderApiException(422, "INVALID_CHECKOUT_KEY", "X-Checkout-Key must be a canonical UUID v4");
        }
        try {
            UUID key = UUID.fromString(value);
            if (key.version() != 4 || key.variant() != 2) {
                throw new IllegalArgumentException("Not UUID v4");
            }
            return key;
        } catch (IllegalArgumentException ex) {
            throw new OrderApiException(422, "INVALID_CHECKOUT_KEY", "X-Checkout-Key must be a UUID v4");
        }
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static void append(StringBuilder out, String value) {
        out.append(value.length()).append(':').append(value).append('|');
    }
}
