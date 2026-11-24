package com.retail.order.dto;

import java.util.List;
import java.util.UUID;

public final class ReservationDtos {
    private ReservationDtos() { }
    public record Request(UUID orderId, List<Item> items) { }
    public record Item(UUID productId, int quantity, long expectedUnitPriceVnd) { }
    public record Response(UUID orderId, String status) { }
}
