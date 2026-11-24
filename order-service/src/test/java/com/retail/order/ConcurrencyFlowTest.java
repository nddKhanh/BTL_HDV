package com.retail.order;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class ConcurrencyFlowTest {
    private static final String INTERNAL_KEY = "dev-internal-key-change-me";
    private static final String PRODUCT_ID = "11111111-1111-4111-8111-111111111111";

    @AfterEach
    void restoreStock() {
        setStock(100);
    }

    @Test
    void concurrentRequestsWithSameCheckoutKeyCreateExactlyOneOrder() {
        String key = UUID.randomUUID().toString();
        String body = requestBody("Same Checkout Key");
        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> checkoutId(key, body));
        CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> checkoutId(key, body));

        String firstId = first.join();
        String secondId = second.join();
        assertEquals(firstId, secondId, "Concurrent retries must resolve to the same order id");

        given().header("X-Internal-Key", INTERNAL_KEY)
                .when().get("/mock-catalog/internal/stock/reservations/" + firstId)
                .then().statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("ACTIVE"));
    }

    @Test
    void twoDifferentOrdersCompetingForOneRemainingUnitOnlyConfirmOne() {
        setStock(1);
        String firstBody = requestBody("Competitor A");
        String secondBody = requestBody("Competitor B");

        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> checkout(UUID.randomUUID().toString(), firstBody));
        CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> checkout(UUID.randomUUID().toString(), secondBody));

        String firstStatus = first.join();
        String secondStatus = second.join();
        long confirmed = java.util.List.of(firstStatus, secondStatus).stream().filter("CONFIRMED"::equals).count();
        long rejected = java.util.List.of(firstStatus, secondStatus).stream().filter("REJECTED"::equals).count();
        assertEquals(1, confirmed, "Exactly one order may reserve the last unit");
        assertEquals(1, rejected, "The other order must be rejected as out of stock");
    }

    @Test
    void concurrentCancelRequestsDoNotFailOrChangeFinalState() {
        String key = UUID.randomUUID().toString();
        String orderId = given().header("X-Checkout-Key", key)
                .contentType("application/json").body(requestBody("Concurrent Cancel"))
                .when().post("/api/orders")
                .then().statusCode(201).extract().path("id");

        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> cancel(key, orderId));
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(() -> cancel(key, orderId));
        assertEquals(200, first.join());
        assertEquals(200, second.join());

        given().header("X-Checkout-Key", key)
                .when().get("/api/orders/" + orderId)
                .then().statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("CANCELLED"));
    }

    private static String checkoutId(String key, String body) {
        var response = given().header("X-Checkout-Key", key).contentType("application/json").body(body)
                .when().post("/api/orders").then().extract().response();
        if (response.statusCode() != 200 && response.statusCode() != 201 && response.statusCode() != 202) {
            throw new AssertionError("Unexpected checkout HTTP status: " + response.statusCode());
        }
        return response.path("id");
    }

    private static String checkout(String key, String body) {
        return given().header("X-Checkout-Key", key).contentType("application/json").body(body)
                .when().post("/api/orders").then().statusCode(201)
                .extract().path("status");
    }

    private static int cancel(String key, String orderId) {
        return given().header("X-Checkout-Key", key)
                .when().post("/api/orders/" + orderId + "/cancel")
                .then().extract().statusCode();
    }

    private static void setStock(int quantity) {
        given().header("X-Internal-Key", INTERNAL_KEY)
                .post("/mock-catalog/internal/test/stock/" + PRODUCT_ID + "/" + quantity)
                .then().statusCode(204);
    }

    private static String requestBody(String customer) {
        return """
                {"customer":{"name":"%s","phone":"0912345678","address":"Ha Noi"},
                 "items":[{"productId":"%s","quantity":1}]}
                """.formatted(customer, PRODUCT_ID);
    }
}
