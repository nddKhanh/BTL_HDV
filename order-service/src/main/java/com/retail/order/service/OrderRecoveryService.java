package com.retail.order.service;

import com.retail.order.dto.ReservationDtos;
import com.retail.order.entity.Order;
import com.retail.order.entity.OrderStatus;
import com.retail.order.exception.CatalogBusinessException;
import com.retail.order.exception.CatalogUnavailableException;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class OrderRecoveryService {
    private static final Logger LOG = Logger.getLogger(OrderRecoveryService.class);
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, AtomicInteger> retryCounts = new ConcurrentHashMap<>();

    @Inject OrderPersistenceService persistence;
    @Inject CatalogGateway catalog;

    @Scheduled(every = "60s")
    void scheduledRecovery() {
        var batch = persistence.findRecoverable(Instant.now().minusSeconds(30), 20);
        for (Order order : batch) {
            tryRecoverOne(order.id);
        }
    }

    public void tryRecoverOne(UUID id) {
        if (!inFlight.add(id)) return;
        try {
            Order order = persistence.findById(id).orElse(null);
            if (order == null) return;
            int attempt = retryCounts.computeIfAbsent(id, ignored -> new AtomicInteger()).incrementAndGet();
            LOG.infof("Recovery attempt orderId=%s status=%s retry=%d", id, order.status, attempt);

            if (order.status == OrderStatus.PENDING) recoverReserve(order);
            else if (order.status == OrderStatus.CANCEL_PENDING) recoverRelease(order);
            else retryCounts.remove(id);
        } finally {
            inFlight.remove(id);
        }
    }

    private void recoverReserve(Order order) {
        try {
            // C3 first resolves the ambiguous case where C2 committed in Catalog
            // but its HTTP response was lost before Order could update its state.
            try {
                ReservationDtos.Response observed = catalog.getReservation(order.id);
                if (observed != null) {
                    if ("ACTIVE".equalsIgnoreCase(observed.status())) {
                        persistence.transition(order.id, OrderStatus.PENDING, OrderStatus.CONFIRMED, null);
                        retryCounts.remove(order.id);
                        LOG.infof("Recovery verified via C3 orderId=%s status=CONFIRMED", order.id);
                        return;
                    }
                    if ("RELEASED".equalsIgnoreCase(observed.status())) {
                        LOG.errorf("Recovery integrity warning orderId=%s Catalog reservation is RELEASED while Order is PENDING", order.id);
                        return;
                    }
                }
            } catch (CatalogUnavailableException ex) {
                // C3 is observational. C2 is idempotent by orderId, so retry it
                // even when C3 is temporarily unavailable.
                LOG.warnf("C3 unavailable for orderId=%s; falling back to idempotent C2", order.id);
            }

            ReservationDtos.Response reservation = catalog.reserve(CheckoutService.reservationRequest(order));
            if ("ACTIVE".equalsIgnoreCase(reservation.status())) {
                persistence.transition(order.id, OrderStatus.PENDING, OrderStatus.CONFIRMED, null);
                retryCounts.remove(order.id);
                LOG.infof("Recovery completed orderId=%s status=CONFIRMED", order.id);
            } else {
                LOG.warnf("Recovery keeps orderId=%s status=PENDING; Catalog status=%s",
                        order.id, reservation.status());
            }
        } catch (CatalogBusinessException ex) {
            persistence.transition(order.id, OrderStatus.PENDING, OrderStatus.REJECTED, ex.code());
            retryCounts.remove(order.id);
            LOG.warnf("Recovery rejected orderId=%s code=%s", order.id, ex.code());
        } catch (CatalogUnavailableException ex) {
            LOG.warnf("Recovery keeps orderId=%s status=PENDING", order.id);
        }
    }

    private void recoverRelease(Order order) {
        try {
            ReservationDtos.Response release = catalog.release(order.id);
            if ("RELEASED".equalsIgnoreCase(release.status())) {
                persistence.transition(order.id, OrderStatus.CANCEL_PENDING, OrderStatus.CANCELLED, null);
                retryCounts.remove(order.id);
                LOG.infof("Recovery completed orderId=%s status=CANCELLED", order.id);
            } else {
                LOG.warnf("Recovery keeps orderId=%s status=CANCEL_PENDING; Catalog status=%s",
                        order.id, release.status());
            }
        } catch (CatalogUnavailableException | CatalogBusinessException ex) {
            LOG.warnf("Recovery keeps orderId=%s status=CANCEL_PENDING", order.id);
        }
    }
}
