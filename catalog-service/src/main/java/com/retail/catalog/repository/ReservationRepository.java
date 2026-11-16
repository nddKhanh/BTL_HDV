package com.retail.catalog.repository;

import com.retail.catalog.entity.StockReservation;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ReservationRepository implements PanacheRepositoryBase<StockReservation, UUID> {

    public Optional<StockReservation> findByIdForUpdate(UUID orderId) {
        return findByIdOptional(orderId, LockModeType.PESSIMISTIC_WRITE);
    }
}
