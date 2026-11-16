package com.retail.catalog.resource;

import com.retail.catalog.entity.Product;
import com.retail.catalog.repository.ProductRepository;
import com.retail.catalog.repository.ReservationItemRepository;
import com.retail.catalog.repository.ReservationRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class InventoryConcurrencyTest {

    private static final String SEED_CATEGORY_IT = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1";

    @ConfigProperty(name = "app.admin-key")
    String adminKey;

    @ConfigProperty(name = "app.internal-key")
    String internalKey;

    @Inject
    ProductRepository productRepository;

    @Inject
    ReservationRepository reservationRepository;

    @Inject
    ReservationItemRepository reservationItemRepository;

    private final List<UUID> createdOrderIds = new ArrayList<>();
    private final List<UUID> createdProductIds = new ArrayList<>();

    @AfterEach
    @Transactional
    void cleanup() {
        for (UUID orderId : createdOrderIds) {
            reservationItemRepository.delete("id.orderId", orderId);
            reservationRepository.deleteById(orderId);
        }
        createdOrderIds.clear();
        for (UUID productId : createdProductIds) {
            productRepository.deleteById(productId);
        }
        createdProductIds.clear();
    }

    private UUID createTestProduct(long priceVnd, int stock) {
        String idStr = given()
                .header("X-Admin-Key", adminKey)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "categoryId", SEED_CATEGORY_IT,
                        "sku", "CONC-" + UUID.randomUUID().toString().substring(0, 8),
                        "name", "Sách Kiểm Thử Kho",
                        "description", "Dùng cho test giữ kho",
                        "priceVnd", priceVnd,
                        "availableStock", stock
                ))
                .when().post("/api/admin/products")
                .then()
                .statusCode(201)
                .extract().path("id");
        UUID id = UUID.fromString(idStr);
        createdProductIds.add(id);
        return id;
    }

    @Test
    void testReserveIdempotencyConflictPriceChangedAndRelease() {
        UUID productA = createTestProduct(100000L, 5);
        UUID productB = createTestProduct(200000L, 5);
        UUID orderId = UUID.randomUUID();
        createdOrderIds.add(orderId);

        // 1. First reserve -> 201 Created ACTIVE
        given()
                .header("X-Internal-Key", internalKey)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "orderId", orderId.toString(),
                        "items", List.of(
                                Map.of("productId", productA.toString(), "quantity", 2, "expectedUnitPriceVnd", 100000),
                                Map.of("productId", productB.toString(), "quantity", 1, "expectedUnitPriceVnd", 200000)
                        )
                ))
                .when().post("/internal/stock/reservations")
                .then()
                .statusCode(201)
                .body("orderId", equalTo(orderId.toString()))
                .body("status", equalTo("ACTIVE"));

        assertEquals(3, getAvailableStock(productA));
        assertEquals(4, getAvailableStock(productB));

        // 2. Idempotent retry with reversed item order -> 200 OK ACTIVE, no extra stock deduction
        given()
                .header("X-Internal-Key", internalKey)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "orderId", orderId.toString(),
                        "items", List.of(
                                Map.of("productId", productB.toString(), "quantity", 1, "expectedUnitPriceVnd", 200000),
                                Map.of("productId", productA.toString(), "quantity", 2, "expectedUnitPriceVnd", 100000)
                        )
                ))
                .when().post("/internal/stock/reservations")
                .then()
                .statusCode(200)
                .body("orderId", equalTo(orderId.toString()))
                .body("status", equalTo("ACTIVE"));

        assertEquals(3, getAvailableStock(productA));
        assertEquals(4, getAvailableStock(productB));

        // 3. Same orderId but different quantity -> 409 RESERVATION_CONFLICT
        given()
                .header("X-Internal-Key", internalKey)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "orderId", orderId.toString(),
                        "items", List.of(
                                Map.of("productId", productA.toString(), "quantity", 3, "expectedUnitPriceVnd", 100000),
                                Map.of("productId", productB.toString(), "quantity", 1, "expectedUnitPriceVnd", 200000)
                        )
                ))
                .when().post("/internal/stock/reservations")
                .then()
                .statusCode(409)
                .body("error.code", equalTo("RESERVATION_CONFLICT"));

        // 4. C3 GET reservation -> 200 ACTIVE
        given()
                .header("X-Internal-Key", internalKey)
                .when().get("/internal/stock/reservations/" + orderId)
                .then()
                .statusCode(200)
                .body("orderId", equalTo(orderId.toString()))
                .body("status", equalTo("ACTIVE"));

        // 5. C4 Release repeated 5 times -> all return 200 RELEASED, stock restored exactly once
        for (int i = 0; i < 5; i++) {
            given()
                    .header("X-Internal-Key", internalKey)
                    .when().post("/internal/stock/reservations/" + orderId + "/release")
                    .then()
                    .statusCode(200)
                    .body("orderId", equalTo(orderId.toString()))
                    .body("status", equalTo("RELEASED"));
        }
        assertEquals(5, getAvailableStock(productA));
        assertEquals(5, getAvailableStock(productB));

        // 6. Re-reserving a RELEASED orderId -> 409 RESERVATION_RELEASED
        given()
                .header("X-Internal-Key", internalKey)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "orderId", orderId.toString(),
                        "items", List.of(
                                Map.of("productId", productA.toString(), "quantity", 2, "expectedUnitPriceVnd", 100000),
                                Map.of("productId", productB.toString(), "quantity", 1, "expectedUnitPriceVnd", 200000)
                        )
                ))
                .when().post("/internal/stock/reservations")
                .then()
                .statusCode(409)
                .body("error.code", equalTo("RESERVATION_RELEASED"));

        // 7. Price mismatch rolls back entire multi-item reservation
        UUID orderPriceMismatch = UUID.randomUUID();
        createdOrderIds.add(orderPriceMismatch);
        given()
                .header("X-Internal-Key", internalKey)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "orderId", orderPriceMismatch.toString(),
                        "items", List.of(
                                Map.of("productId", productA.toString(), "quantity", 1, "expectedUnitPriceVnd", 100000),
                                Map.of("productId", productB.toString(), "quantity", 1, "expectedUnitPriceVnd", 199000)
                        )
                ))
                .when().post("/internal/stock/reservations")
                .then()
                .statusCode(409)
                .body("error.code", equalTo("PRICE_CHANGED"));

        assertEquals(5, getAvailableStock(productA));
        assertEquals(5, getAvailableStock(productB));
    }

    @Test
    void testTwentyConcurrentThreadsCompetingForSingleStockUnit() throws Exception {
        UUID productId = createTestProduct(120000L, 1);
        int threads = 20;
        List<UUID> orderIds = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            UUID id = UUID.randomUUID();
            orderIds.add(id);
            createdOrderIds.add(id);
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger createdCount = new AtomicInteger();
        AtomicInteger outOfStockCount = new AtomicInteger();

        List<Callable<Void>> tasks = new ArrayList<>(threads);
        for (UUID orderId : orderIds) {
            tasks.add(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                var response = given()
                        .header("X-Internal-Key", internalKey)
                        .contentType(ContentType.JSON)
                        .body(Map.of(
                                "orderId", orderId.toString(),
                                "items", List.of(
                                        Map.of("productId", productId.toString(),
                                                "quantity", 1,
                                                "expectedUnitPriceVnd", 120000)
                                )
                        ))
                        .when().post("/internal/stock/reservations");
                int status = response.statusCode();
                if (status == 201) {
                    createdCount.incrementAndGet();
                } else if (status == 409 && "OUT_OF_STOCK".equals(response.jsonPath().getString("error.code"))) {
                    outOfStockCount.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>(threads);
        for (Callable<Void> task : tasks) {
            futures.add(pool.submit(task));
        }
        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        for (Future<Void> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertEquals(1, createdCount.get(), "Exactly 1 reservation must succeed when stock=1");
        assertEquals(19, outOfStockCount.get(), "Remaining 19 requests must fail with 409 OUT_OF_STOCK");
        assertEquals(0, getAvailableStock(productId), "Stock must never go negative");
    }

    @Test
    void testConcurrentSameOrderIdReserveAndConcurrentRelease() throws Exception {
        UUID productId = createTestProduct(150000L, 10);
        UUID orderId = UUID.randomUUID();
        createdOrderIds.add(orderId);

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();

        List<Future<Void>> futures = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                int status = given()
                        .header("X-Internal-Key", internalKey)
                        .contentType(ContentType.JSON)
                        .body(Map.of(
                                "orderId", orderId.toString(),
                                "items", List.of(
                                        Map.of("productId", productId.toString(),
                                                "quantity", 3,
                                                "expectedUnitPriceVnd", 150000)
                                )
                        ))
                        .when().post("/internal/stock/reservations")
                        .statusCode();
                if (status == 201 || status == 200) {
                    successCount.incrementAndGet();
                }
                return null;
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        for (Future<Void> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }

        assertEquals(threads, successCount.get(), "All same-orderId retries must succeed with 201/200");
        assertEquals(7, getAvailableStock(productId), "Stock must be deducted only once (10 - 3 = 7)");

        // Now test 5 concurrent releases on the same orderId
        CountDownLatch releaseReady = new CountDownLatch(5);
        CountDownLatch releaseStart = new CountDownLatch(1);
        AtomicInteger releaseOk = new AtomicInteger();
        List<Future<Void>> releaseFutures = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            releaseFutures.add(pool.submit(() -> {
                releaseReady.countDown();
                releaseStart.await(10, TimeUnit.SECONDS);
                int status = given()
                        .header("X-Internal-Key", internalKey)
                        .when().post("/internal/stock/reservations/" + orderId + "/release")
                        .statusCode();
                if (status == 200) {
                    releaseOk.incrementAndGet();
                }
                return null;
            }));
        }
        releaseReady.await(10, TimeUnit.SECONDS);
        releaseStart.countDown();
        for (Future<Void> f : releaseFutures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertEquals(5, releaseOk.get(), "All concurrent releases must return 200 OK");
        assertEquals(10, getAvailableStock(productId), "Stock must be restored only once (7 + 3 = 10)");
    }

    @Transactional
    int getAvailableStock(UUID productId) {
        Product p = productRepository.findById(productId);
        return p.getAvailableStock();
    }
}
