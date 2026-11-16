package com.retail.catalog.service;

import com.retail.catalog.dto.ProductDto.ProductCreateRequest;
import com.retail.catalog.dto.ProductDto.ProductPage;
import com.retail.catalog.dto.ProductDto.ProductResponse;
import com.retail.catalog.dto.ProductDto.ProductUpdateRequest;
import com.retail.catalog.dto.ProductDto.StockUpdateRequest;
import com.retail.catalog.entity.Product;
import com.retail.catalog.exception.ApiException;
import com.retail.catalog.repository.ProductRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
public class ProductService {

    public static final int MAX_PAGE_SIZE = 100;
    static final String NOT_FOUND = "PRODUCT_NOT_FOUND";

    @Inject
    ProductRepository products;

    @Inject
    CategoryService categoryService;

    // ---------- public (F3) ----------

    @Transactional
    public ProductPage search(int page, int size, String query, String rawCategoryId) {
        if (page < 0) {
            throw ApiException.unprocessable("VALIDATION_ERROR", "page: must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.unprocessable("VALIDATION_ERROR",
                    "size: must be between 1 and " + MAX_PAGE_SIZE);
        }
        UUID categoryId = null;
        if (rawCategoryId != null && !rawCategoryId.isBlank()) {
            try {
                categoryId = UUID.fromString(rawCategoryId.trim());
            } catch (IllegalArgumentException e) {
                throw ApiException.unprocessable("VALIDATION_ERROR", "categoryId: must be a UUID");
            }
        }
        PanacheQuery<Product> q = products.searchActive(query, categoryId, page, size);
        return new ProductPage(q.list().stream().map(ProductResponse::from).toList(),
                page, size, q.count());
    }

    @Transactional
    public ProductResponse getActive(String rawId) {
        UUID id = parseId(rawId);
        return products.findActiveById(id)
                .map(ProductResponse::from)
                .orElseThrow(ProductService::notFound);
    }

    // ---------- admin (F2) ----------

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        String sku = request.sku().trim();
        if (products.existsBySku(sku)) {
            throw skuExists();
        }
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setCategory(categoryService.requireById(request.categoryId()));
        product.setSku(sku);
        product.setName(request.name().trim());
        product.setDescription(ServiceSupport.trimToEmpty(request.description()));
        product.setPriceVnd(request.priceVnd());
        product.setAvailableStock(request.availableStock());
        product.setActive(Boolean.TRUE);
        try {
            products.persist(product);
            products.flush();
        } catch (PersistenceException e) {
            ServiceSupport.rethrowIfNotConstraint(e);
            throw skuExists();
        }
        return ProductResponse.from(product);
    }

    /** Updates name, description, categoryId, priceVnd, active. Stock is never touched here. */
    @Transactional
    public ProductResponse update(String rawId, ProductUpdateRequest request) {
        Product product = lockForWrite(rawId);
        product.setCategory(categoryService.requireById(request.categoryId()));
        product.setName(request.name().trim());
        product.setDescription(ServiceSupport.trimToEmpty(request.description()));
        product.setPriceVnd(request.priceVnd());
        product.setActive(request.active());
        products.flush();
        return ProductResponse.from(product);
    }

    /** Soft delete: active=false, the row is kept for order snapshots/reservations. */
    @Transactional
    public void deactivate(String rawId) {
        Product product = lockForWrite(rawId);
        if (Boolean.TRUE.equals(product.getActive())) {
            product.setActive(Boolean.FALSE);
            products.flush();
        }
    }

    /** Uses the same pessimistic row lock as reserve/release so updates never race with C2/C4. */
    @Transactional
    public ProductResponse updateStock(String rawId, StockUpdateRequest request) {
        Product product = lockForWrite(rawId);
        product.setAvailableStock(request.availableStock());
        products.flush();
        return ProductResponse.from(product);
    }

    private Product lockForWrite(String rawId) {
        UUID id = parseId(rawId);
        return products.findByIdForUpdate(id).orElseThrow(ProductService::notFound);
    }

    private static UUID parseId(String rawId) {
        return ServiceSupport.parseIdOrNotFound(rawId, NOT_FOUND, "Product not found");
    }

    private static ApiException notFound() {
        return ApiException.notFound(NOT_FOUND, "Product not found");
    }

    private static ApiException skuExists() {
        return ApiException.conflict("SKU_EXISTS", "Product SKU already exists");
    }
}
