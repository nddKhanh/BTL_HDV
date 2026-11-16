package com.retail.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite primary key (order_id, product_id). */
@Embeddable
public class StockReservationItemId implements Serializable {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    public StockReservationItemId() {
    }

    public StockReservationItemId(UUID orderId, UUID productId) {
        this.orderId = orderId;
        this.productId = productId;
    }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StockReservationItemId other)) return false;
        return Objects.equals(orderId, other.orderId) && Objects.equals(productId, other.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, productId);
    }
}
