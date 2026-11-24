package com.retail.order.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity(name = "OrderEntity")
@Table(name = "orders")
public class Order extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name = "checkout_key_hash", nullable = false, unique = true, length = 64, columnDefinition = "char(64)")
    public String checkoutKeyHash;

    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    public String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public OrderStatus status;

    @Column(name = "total_vnd", nullable = false)
    public long totalVnd;

    @Column(name = "customer_name", nullable = false, length = 120)
    public String customerName;

    @Column(name = "customer_phone", nullable = false, length = 20)
    public String customerPhone;

    @Column(name = "shipping_address", nullable = false, columnDefinition = "text")
    public String shippingAddress;

    @Column(name = "last_error_code", length = 60)
    public String lastErrorCode;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    public List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.order = this;
        if (item.id != null) item.id.orderId = this.id;
    }
}
