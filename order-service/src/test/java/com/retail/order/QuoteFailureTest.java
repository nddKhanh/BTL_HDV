package com.retail.order;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class QuoteFailureTest {
    private static final String INTERNAL_KEY = "dev-internal-key-change-me";
    private static final String PRODUCT_ID = "11111111-1111-4111-8111-111111111111";

    @Test
    void quoteUnavailableReturns503BeforeOrderIsCreated() {
        given().header("X-Internal-Key", INTERNAL_KEY)
                .post("/mock-catalog/internal/test/fail-next-quote")
                .then().statusCode(204);

        String body = """
                {"customer":{"name":"Quote Test","phone":"0912345678","address":"Ha Noi"},
                 "items":[{"productId":"%s","quantity":1}]}
                """.formatted(PRODUCT_ID);

        given().header("X-Checkout-Key", UUID.randomUUID().toString())
                .contentType("application/json").body(body)
                .when().post("/api/orders")
                .then().statusCode(503)
                .body("error.code", equalTo("CATALOG_UNAVAILABLE"));
    }
}
