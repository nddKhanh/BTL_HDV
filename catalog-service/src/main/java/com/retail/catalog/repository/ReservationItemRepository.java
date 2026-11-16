package com.retail.catalog.repository;

import com.retail.catalog.entity.StockReservationItem;
import com.retail.catalog.entity.StockReservationItemId;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ReservationItemRepository implements PanacheRepositoryBase<StockReservationItem, StockReservationItemId> {

    public List<StockReservationItem> findByOrderIdSorted(UUID orderId) {
        return list("id.orderId = ?1 order by id.productId", orderId);
    }
}
