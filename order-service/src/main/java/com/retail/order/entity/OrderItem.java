package com.retail.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItem {
    @EmbeddedId
    public OrderItemId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("orderId")
    @JoinColumn(name = "order_id", nullable = false)
    public Order order;

    @Column(name = "product_name", nullable = false, length = 200)
    public String productName;

    @Column(nullable = false)
    public int quantity;

    @Column(name = "unit_price_vnd", nullable = false)
    public long unitPriceVnd;

    @Column(name = "line_total_vnd", nullable = false)
    public long lineTotalVnd;

    public OrderItem() { }

    public OrderItem(java.util.UUID productId, String productName, int quantity, long unitPriceVnd, long lineTotalVnd) {
        this.id = new OrderItemId(null, productId);
        this.productName = productName;
        this.quantity = quantity;
        this.unitPriceVnd = unitPriceVnd;
        this.lineTotalVnd = lineTotalVnd;
    }
}
