package com.retail.order;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class OrderResourceTest {
    @Test
    void healthEndpointIsAvailable() {
        given().when().get("/q/health").then().statusCode(200);
    }

    @Test
    void checkoutHeaderIsRequired() {
        given().contentType("application/json")
                .body("""
                      {"customer":{"name":"Nguyen Van A","phone":"0912345678","address":"Ha Noi"},
                       "items":[{"productId":"11111111-1111-4111-8111-111111111111","quantity":2}]}
                      """)
                .when().post("/api/orders")
                .then().statusCode(400)
                .body("error.code", equalTo("CHECKOUT_KEY_REQUIRED"));
    }
}
