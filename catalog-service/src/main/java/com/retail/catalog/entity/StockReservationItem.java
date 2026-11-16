package com.retail.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_reservation_items")
public class StockReservationItem {

    @EmbeddedId
    private StockReservationItemId id = new StockReservationItemId();

    @MapsId("orderId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private StockReservation reservation;

    @MapsId("productId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price_vnd", nullable = false)
    private Long unitPriceVnd;

    public StockReservationItemId getId() { return id; }
    public StockReservation getReservation() { return reservation; }
    public void setReservation(StockReservation reservation) {
        this.reservation = reservation;
        if (reservation != null) {
            this.id.setOrderId(reservation.getOrderId());
        }
    }
    public Product getProduct() { return product; }
    public void setProduct(Product product) {
        this.product = product;
        if (product != null) {
            this.id.setProductId(product.getId());
        }
    }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Long getUnitPriceVnd() { return unitPriceVnd; }
    public void setUnitPriceVnd(Long unitPriceVnd) { this.unitPriceVnd = unitPriceVnd; }
}
