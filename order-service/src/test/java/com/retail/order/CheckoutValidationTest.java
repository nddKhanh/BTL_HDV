package com.retail.order;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class CheckoutValidationTest {
    private static final String PRODUCT_ID = "11111111-1111-4111-8111-111111111111";

    @Test
    void duplicateProductIdsAreRejected() {
        String body = """
                {"customer":{"name":"Validation Test","phone":"0912345678","address":"Ha Noi"},
                 "items":[
                   {"productId":"%s","quantity":1},
                   {"productId":"%s","quantity":2}
                 ]}
                """.formatted(PRODUCT_ID, PRODUCT_ID);

        given().header("X-Checkout-Key", UUID.randomUUID().toString())
                .contentType("application/json").body(body)
                .when().post("/api/orders")
                .then().statusCode(422)
                .body("error.code", equalTo("INVALID_ITEMS"));
    }

    @Test
    void zeroQuantityIsRejected() {
        String body = """
                {"customer":{"name":"Validation Test","phone":"0912345678","address":"Ha Noi"},
                 "items":[{"productId":"%s","quantity":0}]}
                """.formatted(PRODUCT_ID);

        given().header("X-Checkout-Key", UUID.randomUUID().toString())
                .contentType("application/json").body(body)
                .when().post("/api/orders")
                .then().statusCode(422);
    }

    @Test
    void wrongCheckoutKeyCannotReadOrder() {
        String createBody = """
                {"customer":{"name":"Authorization Test","phone":"0912345678","address":"Ha Noi"},
                 "items":[{"productId":"%s","quantity":1}]}
                """.formatted(PRODUCT_ID);
        String ownerKey = UUID.randomUUID().toString();

        String orderId = given().header("X-Checkout-Key", ownerKey)
                .contentType("application/json").body(createBody)
                .when().post("/api/orders")
                .then().statusCode(201)
                .extract().path("id");

        given().header("X-Checkout-Key", UUID.randomUUID().toString())
                .when().get("/api/orders/" + orderId)
                .then().statusCode(403)
                .body("error.code", equalTo("INVALID_CHECKOUT_KEY"));
    }
}
