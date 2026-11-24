package com.retail.order.dto;

import java.util.List;
import java.util.UUID;

public final class QuoteDtos {
    private QuoteDtos() { }
    public record Request(List<ItemRequest> items) { }
    public record ItemRequest(UUID productId, int quantity) { }
    public record Response(String currency, List<Item> items, long totalVnd) { }
    public record Item(UUID productId, String sku, String name, int quantity, long unitPriceVnd, long lineTotalVnd) { }
}
