package com.retail.catalog.dto;

import com.retail.catalog.entity.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProductDto {

    private ProductDto() {
    }

    public record ProductResponse(
            UUID id,
            String sku,
            String name,
            String description,
            UUID categoryId,
            Long priceVnd,
            Integer availableStock,
            Boolean active,
            Instant createdAt,
            Instant updatedAt) {

        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(),
                    p.getCategory().getId(), p.getPriceVnd(), p.getAvailableStock(), p.getActive(),
                    p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    public record ProductCreateRequest(
            @NotNull(message = "must not be null")
            UUID categoryId,

            @NotBlank(message = "must not be blank")
            @Size(max = 80, message = "must be at most 80 characters")
            String sku,

            @NotBlank(message = "must not be blank")
            @Size(max = 200, message = "must be at most 200 characters")
            String name,

            @Size(max = 10000, message = "must be at most 10000 characters")
            String description,

            @NotNull(message = "must not be null")
            @PositiveOrZero(message = "must be >= 0")
            Long priceVnd,

            @NotNull(message = "must not be null")
            @PositiveOrZero(message = "must be >= 0")
            Integer availableStock) {
    }

    /** PUT never changes stock or SKU; use PATCH /stock for stock. */
    public record ProductUpdateRequest(
            @NotNull(message = "must not be null")
            UUID categoryId,

            @NotBlank(message = "must not be blank")
            @Size(max = 200, message = "must be at most 200 characters")
            String name,

            @Size(max = 10000, message = "must be at most 10000 characters")
            String description,

            @NotNull(message = "must not be null")
            @PositiveOrZero(message = "must be >= 0")
            Long priceVnd,

            @NotNull(message = "must not be null")
            Boolean active) {
    }

    public record StockUpdateRequest(
            @NotNull(message = "must not be null")
            @PositiveOrZero(message = "must be >= 0")
            Integer availableStock) {
    }

    public record ProductPage(List<ProductResponse> items, int page, int size, long total) {
    }
}
