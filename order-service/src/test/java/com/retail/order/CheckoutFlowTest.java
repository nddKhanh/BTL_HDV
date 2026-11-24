package com.retail.order;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class CheckoutFlowTest {
    private static final String PRODUCT_ID = "11111111-1111-4111-8111-111111111111";

    @Test
    void checkoutIsIdempotentAndCanBeCancelled() {
        String key = UUID.randomUUID().toString();
        String body = """
                {"customer":{"name":"Test Customer","phone":"0912345678","address":"Ha Noi"},
                 "items":[{"productId":"%s","quantity":2}]}
                """.formatted(PRODUCT_ID);

        String orderId = given().header("X-Checkout-Key", key)
                .contentType("application/json").body(body)
                .when().post("/api/orders")
                .then().statusCode(201)
                .body("status", equalTo("CONFIRMED"))
                .body("totalVnd", equalTo(398000))
                .body("id", notNullValue())
                .extract().path("id");

        // Same key + same normalized body returns the existing order, not a second reservation.
        given().header("X-Checkout-Key", key)
                .contentType("application/json").body(body)
                .when().post("/api/orders")
                .then().statusCode(200)
                .body("id", equalTo(orderId));

        given().header("X-Checkout-Key", key)
                .when().get("/api/orders/" + orderId)
                .then().statusCode(200)
                .body("status", equalTo("CONFIRMED"));

        given().header("X-Checkout-Key", key)
                .when().post("/api/orders/" + orderId + "/cancel")
                .then().statusCode(200)
                .body("status", equalTo("CANCELLED"));

        // Repeating cancel must remain successful and must not release stock again.
        given().header("X-Checkout-Key", key)
                .when().post("/api/orders/" + orderId + "/cancel")
                .then().statusCode(200)
                .body("status", equalTo("CANCELLED"));
    }

    @Test
    void sameCheckoutKeyWithDifferentBodyReturnsConflict() {
        String key = UUID.randomUUID().toString();
        String first = """
                {"customer":{"name":"Test Customer","phone":"0912345678","address":"Ha Noi"},
                 "items":[{"productId":"%s","quantity":1}]}
                """.formatted(PRODUCT_ID);
        String second = """
                {"customer":{"name":"Test Customer","phone":"0912345678","address":"Ha Noi"},
                 "items":[{"productId":"%s","quantity":3}]}
                """.formatted(PRODUCT_ID);

        given().header("X-Checkout-Key", key).contentType("application/json").body(first)
                .when().post("/api/orders").then().statusCode(201);
        given().header("X-Checkout-Key", key).contentType("application/json").body(second)
                .when().post("/api/orders").then().statusCode(409)
                .body("error.code", equalTo("CHECKOUT_KEY_REUSED"));
    }
}
