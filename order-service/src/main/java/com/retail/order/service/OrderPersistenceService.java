package com.retail.order.service;

import com.retail.order.dto.CheckoutRequest;
import com.retail.order.dto.QuoteDtos;
import com.retail.order.entity.Order;
import com.retail.order.entity.OrderItem;
import com.retail.order.entity.OrderStatus;
import com.retail.order.repository.OrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class OrderPersistenceService {
    @Inject OrderRepository orders;

    @Transactional
    public Optional<Order> findByCheckoutKeyHash(String hash) {
        return orders.findByCheckoutKeyHash(hash);
    }

    @Transactional
    public Optional<Order> findById(UUID id) {
        return orders.findByIdWithItems(id);
    }

    @Transactional
    public Order createPending(UUID id, String checkoutHash, String requestHash,
                               CheckoutRequest request, QuoteDtos.Response quote) {
        Instant now = Instant.now();
        Order order = new Order();
        order.id = id;
        order.checkoutKeyHash = checkoutHash;
        order.requestHash = requestHash;
        order.status = OrderStatus.PENDING;
        order.totalVnd = quote.totalVnd();
        order.customerName = request.customer().name();
        order.customerPhone = request.customer().phone();
        order.shippingAddress = request.customer().address();
        order.createdAt = now;
        order.updatedAt = now;

        for (QuoteDtos.Item quoted : quote.items()) {
            order.addItem(new OrderItem(quoted.productId(), quoted.name(), quoted.quantity(),
                    quoted.unitPriceVnd(), quoted.lineTotalVnd()));
        }
        orders.persist(order);
        orders.flush();
        return order;
    }

    @Transactional
    public boolean transition(UUID id, OrderStatus expected, OrderStatus target, String errorCode) {
        long changed = Order.update("status = ?1, lastErrorCode = ?2, updatedAt = ?3 where id = ?4 and status = ?5",
                target, errorCode, Instant.now(), id, expected);
        return changed == 1;
    }

    @Transactional
    public List<Order> findRecoverable(Instant olderThan, int limit) {
        return orders.findRecoverable(olderThan, limit);
    }
}
