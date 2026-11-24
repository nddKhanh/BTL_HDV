package com.retail.order.service;

import com.retail.order.dto.CheckoutResponse;
import com.retail.order.entity.Order;
import com.retail.order.entity.OrderItem;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Comparator;

@ApplicationScoped
public class OrderResponseMapper {
    public CheckoutResponse toResponse(Order order) {
        var items = order.items.stream()
                .sorted(Comparator.comparing(item -> item.id.productId.toString()))
                .map(this::toItem).toList();
        return new CheckoutResponse(order.id, order.status.name(), "VND", order.totalVnd,
                items, order.lastErrorCode, order.createdAt);
    }

    private CheckoutResponse.Item toItem(OrderItem item) {
        return new CheckoutResponse.Item(item.id.productId, item.productName, item.quantity,
                item.unitPriceVnd, item.lineTotalVnd);
    }
}
