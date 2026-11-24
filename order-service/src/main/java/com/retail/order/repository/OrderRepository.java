package com.retail.order.repository;

import com.retail.order.entity.Order;
import com.retail.order.entity.OrderStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class OrderRepository implements PanacheRepositoryBase<Order, UUID> {
    public Optional<Order> findByCheckoutKeyHash(String hash) {
        return find("select distinct o from OrderEntity o left join fetch o.items where o.checkoutKeyHash = ?1", hash).firstResultOptional();
    }

    public Optional<Order> findByIdWithItems(UUID id) {
        return find("select distinct o from OrderEntity o left join fetch o.items where o.id = ?1", id).firstResultOptional();
    }

    public List<Order> findRecoverable(Instant olderThan, int limit) {
        return find("status in ?1 and updatedAt < ?2 order by createdAt asc",
                List.of(OrderStatus.PENDING, OrderStatus.CANCEL_PENDING), olderThan)
                .page(0, limit).list();
    }
}
