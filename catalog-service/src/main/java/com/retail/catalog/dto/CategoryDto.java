package com.retail.catalog.dto;

import com.retail.catalog.entity.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class CategoryDto {

    private CategoryDto() {
    }

    /** Response: {"id":"...","name":"..."} */
    public record CategoryResponse(UUID id, String name) {
        public static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getName());
        }
    }

    /** Request body for POST/PUT /api/admin/categories. */
    public record CategoryRequest(
            @NotBlank(message = "must not be blank")
            @Size(max = 120, message = "must be at most 120 characters")
            String name) {
    }
}
