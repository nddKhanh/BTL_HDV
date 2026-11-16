package com.retail.catalog.resource;

import com.retail.catalog.repository.CategoryRepository;
import com.retail.catalog.repository.ProductRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ProductResourceTest {

    private static final String SEED_CATEGORY_IT = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1";
    private static final String SEED_PRODUCT_1 = "11111111-1111-4111-8111-111111111111";
    private static final String SEED_PRODUCT_2 = "11111111-1111-4111-8111-111111111112";

    @ConfigProperty(name = "app.admin-key")
    String adminKey;

    @ConfigProperty(name = "app.internal-key")
    String internalKey;

    @Inject
    ProductRepository productRepository;

    @Inject
    CategoryRepository categoryRepository;

    private final List<UUID> createdProductIds = new ArrayList<>();
    private final List<UUID> createdCategoryIds = new ArrayList<>();

    @AfterEach
    @Transactional
    void cleanup() {
        for (UUID productId : createdProductIds) {
            productRepository.deleteById(productId);
        }
        createdProductIds.clear();
        for (UUID categoryId : createdCategoryIds) {
            categoryRepository.deleteById(categoryId);
        }
        createdCategoryIds.clear();
    }

    @Test
    void testSecurityAndRequestIdHeaders() {
        given()
                .header("X-Request-Id", "trace-auth-001")
                .contentType(ContentType.JSON)
                .body(Map.of("name", "Unauthorized Cat"))
                .when().post("/api/admin/categories")
                .then()
                .statusCode(401)
                .header("X-Request-Id", "trace-auth-001")
                .body("error.code", equalTo("UNAUTHORIZED"))
                .body("error.requestId", equalTo("trace-auth-001"));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("items", List.of()))
                .when().post("/internal/catalog/quote")
                .then()
                .statusCode(401)
                .header("X-Request-Id", notNullValue())
                .body("error.code", equalTo("UNAUTHORIZED"));
    }

    @Test
    void testCategoryCrudAndConstraints() {
        String catName = "Danh mục Test " + UUID.randomUUID().toString().substring(0, 8);

        String idStr = given()
                .header("X-Admin-Key", adminKey)
                .contentType(ContentType.JSON)
                .body(Map.of("name", "  " + catName + "  "))
                .when().post("/api/admin/categories")
                .then()
                .statusCode(201)
                .body("name", equalTo(catName))
                .extract().path("id");
        UUID catId = UUID.fromString(idStr);
        createdCategoryIds.add(catId);

        // Duplicate category name -> 409 CATEGORY_EXISTS
        given()
                .header("X-Admin-Key", adminKey)
                .contentType(ContentType.JSON)
                .body(Map.of("name", catName))
                .when().post("/api/admin/categories")
                .then()
                .statusCode(409)
                .body("error.code", equalTo("CATEGORY_EXISTS"));

        // Delete category that is referenced by seeded books -> 409 CATEGORY_IN_USE
        given()
                .header("X-Admin-Key", adminKey)
                .when().delete("/api/admin/categories/" + SEED_CATEGORY_IT)
                .then()
                .statusCode(409)
                .body("error.code", equalTo("CATEGORY_IN_USE"));

        // Delete unused category -> 204
        given()
                .header("X-Admin-Key", adminKey)
                .when().delete("/api/admin/categories/" + catId)
                .then()
                .statusCode(204);
        createdCategoryIds.remove(catId);
    }

    @Test
    void testProductCrudStockAndPublicSearch() {
        String sku = "TEST-SKU-" + UUID.randomUUID().toString().substring(0, 8);
        String idStr = given()
                .header("X-Admin-Key", adminKey)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "categoryId", SEED_CATEGORY_IT,
                        "sku", sku,
                        "name", "Sách Lập Trình Quarkus Nâng Cao",
                        "description", "Mô tả chi tiết",
                        "priceVnd", 150000,
                        "availableStock", 10
                ))
                .when().post("/api/admin/products")
                .then()
                .statusCode(201)
                .body("sku", equalTo(sku))
                .body("availableStock", equalTo(10))
                .body("active", equalTo(true))
                .extract().path("id");
        UUID productId = UUID.fromString(idStr);
        createdProductIds.add(productId);

        // PUT updates metadata/price but never modifies availableStock
        given()
                .header("X-Admin-Key", adminKey)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "categoryId", SEED_CATEGORY_IT,
                        "name", "Sách Lập Trình Quarkus Nâng Cao 2nd",
                        "description", "Bản cập nhật",
                        "priceVnd", 175000,
                        "active", true,
                        "availableStock", 999
                ))
                .when().put("/api/admin/products/" + productId)
                .then()
                .statusCode(200)
                .body("priceVnd", equalTo(175000))
                .body("availableStock", equalTo(10));

        // PATCH /stock updates availableStock
        given()
                .header("X-Admin-Key", adminKey)
                .contentType(ContentType.JSON)
                .body(Map.of("availableStock", 25))
                .when().patch("/api/admin/products/" + productId + "/stock")
                .then()
                .statusCode(200)
                .body("availableStock", equalTo(25));

        // Public search case-insensitive
        given()
                .queryParam("query", "quarkus nâng cao")
                .queryParam("categoryId", SEED_CATEGORY_IT)
                .when().get("/api/products")
                .then()
                .statusCode(200)
                .body("total", greaterThanOrEqualTo(1));

        // Soft delete -> hidden from public GET
        given()
                .header("X-Admin-Key", adminKey)
                .when().delete("/api/admin/products/" + productId)
                .then()
                .statusCode(204);

        given()
                .when().get("/api/products/" + productId)
                .then()
                .statusCode(404)
                .body("error.code", equalTo("PRODUCT_NOT_FOUND"));
    }

    @Test
    void testContractC1Quote() {
        // Valid quote for 2 seeded books (250000 * 2 + 189000 * 1 = 689000)
        given()
                .header("X-Internal-Key", internalKey)
                .contentType(ContentType.JSON)
                .body(Map.of("items", List.of(
                        Map.of("productId", SEED_PRODUCT_1, "quantity", 2),
                        Map.of("productId", SEED_PRODUCT_2, "quantity", 1)
                )))
                .when().post("/internal/catalog/quote")
                .then()
                .statusCode(200)
                .body("currency", equalTo("VND"))
                .body("items.size()", equalTo(2))
                .body("items[0].unitPriceVnd", equalTo(250000))
                .body("items[0].lineTotalVnd", equalTo(500000))
                .body("items[1].unitPriceVnd", equalTo(189000))
                .body("items[1].lineTotalVnd", equalTo(189000))
                .body("totalVnd", equalTo(689000));

        // Duplicate productId -> 422 INVALID_ITEMS
        given()
                .header("X-Internal-Key", internalKey)
                .contentType(ContentType.JSON)
                .body(Map.of("items", List.of(
                        Map.of("productId", SEED_PRODUCT_1, "quantity", 1),
                        Map.of("productId", SEED_PRODUCT_1, "quantity", 2)
                )))
                .when().post("/internal/catalog/quote")
                .then()
                .statusCode(422)
                .body("error.code", equalTo("INVALID_ITEMS"));

        // Quantity out of range (100) -> 422 INVALID_ITEMS
        given()
                .header("X-Internal-Key", internalKey)
                .contentType(ContentType.JSON)
                .body(Map.of("items", List.of(
                        Map.of("productId", SEED_PRODUCT_1, "quantity", 100)
                )))
                .when().post("/internal/catalog/quote")
                .then()
                .statusCode(422)
                .body("error.code", equalTo("INVALID_ITEMS"));

        // Non-existent product -> 404 PRODUCT_NOT_FOUND
        given()
                .header("X-Internal-Key", internalKey)
                .contentType(ContentType.JSON)
                .body(Map.of("items", List.of(
                        Map.of("productId", UUID.randomUUID().toString(), "quantity", 1)
                )))
                .when().post("/internal/catalog/quote")
                .then()
                .statusCode(404)
                .body("error.code", equalTo("PRODUCT_NOT_FOUND"));

        // Inactive product -> 409 PRODUCT_INACTIVE
        String inactiveIdStr = given()
                .header("X-Admin-Key", adminKey)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "categoryId", SEED_CATEGORY_IT,
                        "sku", "INACTIVE-" + UUID.randomUUID().toString().substring(0, 8),
                        "name", "Sách Ngừng Bán",
                        "description", "",
                        "priceVnd", 100000,
                        "availableStock", 5
                ))
                .when().post("/api/admin/products")
                .then().statusCode(201).extract().path("id");
        UUID inactiveId = UUID.fromString(inactiveIdStr);
        createdProductIds.add(inactiveId);

        given()
                .header("X-Admin-Key", adminKey)
                .when().delete("/api/admin/products/" + inactiveId)
                .then().statusCode(204);

        given()
                .header("X-Internal-Key", internalKey)
                .contentType(ContentType.JSON)
                .body(Map.of("items", List.of(
                        Map.of("productId", inactiveId.toString(), "quantity", 1)
                )))
                .when().post("/internal/catalog/quote")
                .then()
                .statusCode(409)
                .body("error.code", equalTo("PRODUCT_INACTIVE"));
    }

    @Test
    void testContractC5AiKnowledgeFeedPagination() {
        long existingActive = countActiveProducts();
        int toCreate = (int) Math.max(0, 101 - existingActive);
        createBulkActiveProducts(toCreate);

        // Paginate 50, 50, 1 across 101 active products
        Set<String> seenIds = new HashSet<>();
        List<Integer> pageSizes = new ArrayList<>();
        Integer offset = 0;
        while (offset != null) {
            var response = given()
                    .header("X-Internal-Key", internalKey)
                    .queryParam("offset", offset)
                    .queryParam("limit", 50)
                    .when().get("/internal/ai/products")
                    .then()
                    .statusCode(200)
                    .body("items[0]", hasKey("id"))
                    .body("items[0]", hasKey("sku"))
                    .body("items[0]", hasKey("name"))
                    .body("items[0]", hasKey("description"))
                    .body("items[0]", hasKey("categoryName"))
                    .body("items[0]", not(hasKey("priceVnd")))
                    .body("items[0]", not(hasKey("availableStock")))
                    .extract().jsonPath();

            List<String> pageIds = response.getList("items.id", String.class);
            List<String> descriptions = response.getList("items.description", String.class);
            pageSizes.add(pageIds.size());
            for (String id : pageIds) {
                assertTrue(seenIds.add(id), "Product ID should not repeat across pages: " + id);
            }
            for (String desc : descriptions) {
                assertEquals(true, desc != null, "Empty description must be returned as \"\", never null");
            }
            offset = response.get("nextOffset");
        }
        assertNull(offset, "Pagination should terminate with nextOffset = null");
        assertEquals(List.of(50, 50, 1), pageSizes, "101 active products with limit=50 must paginate as 50, 50, 1");
        assertEquals(101, seenIds.size(), "All 101 active products must be returned without duplicates or omissions");

        // limit > 100 -> 422 VALIDATION_ERROR
        given()
                .header("X-Internal-Key", internalKey)
                .queryParam("offset", 0)
                .queryParam("limit", 101)
                .when().get("/internal/ai/products")
                .then()
                .statusCode(422)
                .body("error.code", equalTo("VALIDATION_ERROR"));
    }

    @Transactional
    long countActiveProducts() {
        return productRepository.count("active = true");
    }

    @Transactional
    void createBulkActiveProducts(int count) {
        var category = categoryRepository.findById(UUID.fromString(SEED_CATEGORY_IT));
        for (int i = 0; i < count; i++) {
            var p = new com.retail.catalog.entity.Product();
            UUID id = UUID.randomUUID();
            p.setId(id);
            p.setCategory(category);
            p.setSku("BULK-C5-" + id.toString().substring(0, 12));
            p.setName("Sách Bulk C5 #" + i);
            p.setDescription("");
            p.setPriceVnd(99000L);
            p.setAvailableStock(5);
            p.setActive(true);
            productRepository.persist(p);
            createdProductIds.add(id);
        }
    }
}
