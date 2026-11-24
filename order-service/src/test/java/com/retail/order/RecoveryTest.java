package com.retail.order;

import com.retail.order.service.OrderRecoveryService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class RecoveryTest {
    private static final String INTERNAL_KEY = "dev-internal-key-change-me";
    private static final String PRODUCT_ID = "11111111-1111-4111-8111-111111111111";

    @Inject OrderRecoveryService recovery;

    @Test
    void pendingCheckoutConvergesToConfirmedAfterCatalogReturns() {
        given().header("X-Internal-Key", INTERNAL_KEY)
                .post("/mock-catalog/internal/test/fail-next-reserve")
                .then().statusCode(204);

        String checkoutKey = UUID.randomUUID().toString();
        String body = """
                {"customer":{"name":"Recovery Test","phone":"0912345678","address":"Ha Noi"},
                 "items":[{"productId":"%s","quantity":1}]}
                """.formatted(PRODUCT_ID);

        String orderId = given().header("X-Checkout-Key", checkoutKey)
                .contentType("application/json").body(body)
                .when().post("/api/orders")
                .then().statusCode(202)
                .body("status", equalTo("PENDING"))
                .body("id", notNullValue())
                .extract().path("id");

        recovery.tryRecoverOne(UUID.fromString(orderId));

        given().header("X-Checkout-Key", checkoutKey)
                .when().get("/api/orders/" + orderId)
                .then().statusCode(200)
                .body("status", equalTo("CONFIRMED"));
    }

    @Test
    void cancelPendingConvergesToCancelledAfterCatalogReturns() {
        String checkoutKey = UUID.randomUUID().toString();
        String body = """
                {"customer":{"name":"Recovery Test","phone":"0912345678","address":"Ha Noi"},
                 "items":[{"productId":"%s","quantity":1}]}
                """.formatted(PRODUCT_ID);

        String orderId = given().header("X-Checkout-Key", checkoutKey)
                .contentType("application/json").body(body)
                .when().post("/api/orders")
                .then().statusCode(201)
                .body("status", equalTo("CONFIRMED"))
                .extract().path("id");

        given().header("X-Internal-Key", INTERNAL_KEY)
                .post("/mock-catalog/internal/test/fail-next-release")
                .then().statusCode(204);

        given().header("X-Checkout-Key", checkoutKey)
                .when().post("/api/orders/" + orderId + "/cancel")
                .then().statusCode(202)
                .body("status", equalTo("CANCEL_PENDING"));

        recovery.tryRecoverOne(UUID.fromString(orderId));

        given().header("X-Checkout-Key", checkoutKey)
                .when().get("/api/orders/" + orderId)
                .then().statusCode(200)
                .body("status", equalTo("CANCELLED"));
    }
}
