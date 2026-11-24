package com.retail.order;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class CatalogContractTest {
    private static final String INTERNAL_KEY = "dev-internal-key-change-me";
    private static final String PRODUCT_ID = "11111111-1111-4111-8111-111111111111";

    @Test
    void c3ReturnsActiveThenReleasedReservation() {
        String checkoutKey = UUID.randomUUID().toString();
        String body = """
                {"customer":{"name":"C3 Test","phone":"0912345678","address":"Ha Noi"},
                 "items":[{"productId":"%s","quantity":1}]}
                """.formatted(PRODUCT_ID);
        String orderId = given().header("X-Checkout-Key", checkoutKey)
                .contentType("application/json").body(body)
                .when().post("/api/orders")
                .then().statusCode(201).body("status", equalTo("CONFIRMED"))
                .extract().path("id");

        given().header("X-Internal-Key", INTERNAL_KEY)
                .when().get("/mock-catalog/internal/stock/reservations/" + orderId)
                .then().statusCode(200).body("status", equalTo("ACTIVE"));

        given().header("X-Checkout-Key", checkoutKey)
                .when().post("/api/orders/" + orderId + "/cancel")
                .then().statusCode(200).body("status", equalTo("CANCELLED"));

        given().header("X-Internal-Key", INTERNAL_KEY)
                .when().get("/mock-catalog/internal/stock/reservations/" + orderId)
                .then().statusCode(200).body("status", equalTo("RELEASED"));
    }

    @Test
    void c2PriceChangedRejectsOrderWithExactBusinessCode() {
        given().header("X-Internal-Key", INTERNAL_KEY)
                .post("/mock-catalog/internal/test/fail-next-price-changed")
                .then().statusCode(204);

        String body = """
                {"customer":{"name":"Price Test","phone":"0912345678","address":"Ha Noi"},
                 "items":[{"productId":"%s","quantity":1}]}
                """.formatted(PRODUCT_ID);
        given().header("X-Checkout-Key", UUID.randomUUID().toString())
                .contentType("application/json").body(body)
                .when().post("/api/orders")
                .then().statusCode(201)
                .body("status", equalTo("REJECTED"))
                .body("lastErrorCode", equalTo("PRICE_CHANGED"));
    }
}
