package com.retail.order.resource;

import com.retail.order.dto.QuoteDtos;
import com.retail.order.dto.ReservationDtos;
import com.retail.order.exception.OrderApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Local-only Catalog stub. Disable it when connecting to the real Catalog Service. */
@Path("/mock-catalog/internal")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MockCatalogResource {
    private static final UUID DEMO_PRODUCT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final Map<UUID, Integer> stock = new HashMap<>();
    private final Map<UUID, ReservationRecord> reservations = new HashMap<>();
    private final AtomicInteger failQuote = new AtomicInteger();
    private final AtomicInteger failReserve = new AtomicInteger();
    private final AtomicInteger failRelease = new AtomicInteger();
    private final AtomicInteger failPriceChanged = new AtomicInteger();
    @ConfigProperty(name = "app.mock-catalog.enabled", defaultValue = "true") boolean enabled;
    @ConfigProperty(name = "app.mock-catalog.test-controls-enabled", defaultValue = "false") boolean testControlsEnabled;
    @ConfigProperty(name = "app.internal-key") String internalKey;

    public MockCatalogResource() { stock.put(DEMO_PRODUCT, 100); }

    @POST
    @Path("/catalog/quote")
    public QuoteDtos.Response quote(@HeaderParam("X-Internal-Key") String key, QuoteDtos.Request request) {
        checkEnabledAndKey(key);
        failIfRequested(failQuote, "MOCK_QUOTE_TIMEOUT");
        if (request == null || request.items() == null || request.items().isEmpty()) throw new OrderApiException(422, "INVALID_ITEMS", "Items are required");
        List<QuoteDtos.Item> result = new ArrayList<>();
        long total = 0;
        for (QuoteDtos.ItemRequest item : request.items()) {
            if (!DEMO_PRODUCT.equals(item.productId())) throw new OrderApiException(404, "PRODUCT_NOT_FOUND", "Product not found");
            if (item.quantity() < 1 || item.quantity() > 99) throw new OrderApiException(422, "INVALID_ITEMS", "Quantity is invalid");
            long line = 199000L * item.quantity();
            total = Math.addExact(total, line);
            result.add(new QuoteDtos.Item(DEMO_PRODUCT, "TS-001", "Ao thun basic (Mock Catalog)", item.quantity(), 199000L, line));
        }
        return new QuoteDtos.Response("VND", result, total);
    }

    @POST
    @Path("/stock/reservations")
    public synchronized ReservationDtos.Response reserve(@HeaderParam("X-Internal-Key") String key, ReservationDtos.Request request) {
        checkEnabledAndKey(key);
        failIfRequested(failReserve, "MOCK_RESERVE_TIMEOUT");
        failIfRequested(failPriceChanged, "PRICE_CHANGED", 409);
        if (request == null || request.orderId() == null || request.items() == null || request.items().isEmpty()) throw new OrderApiException(422, "INVALID_ITEMS", "Reservation request is invalid");
        ReservationRecord old = reservations.get(request.orderId());
        if (old != null) {
            if (!old.items.equals(request.items())) throw new OrderApiException(409, "RESERVATION_CONFLICT", "Reservation payload changed");
            if ("RELEASED".equals(old.status)) throw new OrderApiException(409, "RESERVATION_RELEASED", "Reservation was already released");
            return new ReservationDtos.Response(request.orderId(), "ACTIVE");
        }
        for (ReservationDtos.Item item : request.items()) {
            if (!DEMO_PRODUCT.equals(item.productId())) throw new OrderApiException(404, "PRODUCT_NOT_FOUND", "Product not found");
            if (item.expectedUnitPriceVnd() != 199000L) throw new OrderApiException(409, "PRICE_CHANGED", "Product price changed");
            if (stock.getOrDefault(item.productId(), 0) < item.quantity()) throw new OrderApiException(409, "OUT_OF_STOCK", "Insufficient available stock");
        }
        for (ReservationDtos.Item item : request.items()) stock.compute(item.productId(), (id, current) -> current - item.quantity());
        reservations.put(request.orderId(), new ReservationRecord(List.copyOf(request.items()), "ACTIVE"));
        return new ReservationDtos.Response(request.orderId(), "ACTIVE");
    }

    @GET
    @Path("/stock/reservations/{orderId}")
    public synchronized ReservationDtos.Response getReservation(@HeaderParam("X-Internal-Key") String key, @PathParam("orderId") UUID orderId) {
        checkEnabledAndKey(key);
        ReservationRecord record = reservations.get(orderId);
        if (record == null) throw new OrderApiException(404, "RESERVATION_NOT_FOUND", "Reservation not found");
        return new ReservationDtos.Response(orderId, record.status);
    }

    @POST
    @Path("/stock/reservations/{orderId}/release")
    public synchronized ReservationDtos.Response release(@HeaderParam("X-Internal-Key") String key, @PathParam("orderId") UUID orderId) {
        checkEnabledAndKey(key);
        failIfRequested(failRelease, "MOCK_RELEASE_TIMEOUT");
        ReservationRecord record = reservations.get(orderId);
        if (record == null) throw new OrderApiException(404, "RESERVATION_NOT_FOUND", "Reservation not found");
        if ("ACTIVE".equals(record.status)) {
            for (ReservationDtos.Item item : record.items) stock.compute(item.productId(), (id, current) -> current + item.quantity());
            record.status = "RELEASED";
        }
        return new ReservationDtos.Response(orderId, "RELEASED");
    }


    @POST
    @Path("/test/fail-next-quote")
    public void failNextQuote(@HeaderParam("X-Internal-Key") String key) {
        checkTestControls(key);
        failQuote.incrementAndGet();
    }

    @POST
    @Path("/test/fail-next-reserve")
    public void failNextReserve(@HeaderParam("X-Internal-Key") String key) {
        checkTestControls(key);
        failReserve.incrementAndGet();
    }

    @POST
    @Path("/test/fail-next-release")
    public void failNextRelease(@HeaderParam("X-Internal-Key") String key) {
        checkTestControls(key);
        failRelease.incrementAndGet();
    }

    @POST
    @Path("/test/fail-next-price-changed")
    public void failNextPriceChanged(@HeaderParam("X-Internal-Key") String key) {
        checkTestControls(key);
        failPriceChanged.incrementAndGet();
    }

    @POST
    @Path("/test/stock/{productId}/{quantity}")
    public synchronized void setStock(@HeaderParam("X-Internal-Key") String key,
                                      @PathParam("productId") UUID productId,
                                      @PathParam("quantity") int quantity) {
        checkTestControls(key);
        if (!DEMO_PRODUCT.equals(productId) || quantity < 0) {
            throw new OrderApiException(422, "INVALID_TEST_STOCK", "Invalid test stock setting");
        }
        stock.put(productId, quantity);
    }

    private void checkTestControls(String key) {
        if (!testControlsEnabled) throw new OrderApiException(404, "NOT_FOUND", "Not found");
        checkEnabledAndKey(key);
    }

    private static void failIfRequested(AtomicInteger counter, String code) {
        failIfRequested(counter, code, 503);
    }

    private static void failIfRequested(AtomicInteger counter, String code, int status) {
        while (true) {
            int current = counter.get();
            if (current <= 0) return;
            if (counter.compareAndSet(current, current - 1)) {
                throw new OrderApiException(status, code,
                        status == 503 ? "Simulated temporary Catalog failure" : "Simulated deterministic Catalog business error");
            }
        }
    }

    private void checkEnabledAndKey(String key) {
        if (!enabled) throw new OrderApiException(404, "MOCK_DISABLED", "Mock Catalog is disabled");
        if (key == null || !key.equals(internalKey)) throw new OrderApiException(401, "UNAUTHORIZED", "Invalid internal key");
    }

    private static final class ReservationRecord {
        final List<ReservationDtos.Item> items;
        String status;
        ReservationRecord(List<ReservationDtos.Item> items, String status) { this.items = items; this.status = status; }
    }
}
