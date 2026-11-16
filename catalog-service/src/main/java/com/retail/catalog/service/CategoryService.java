package com.retail.catalog.service;

import com.retail.catalog.dto.CategoryDto.CategoryRequest;
import com.retail.catalog.dto.CategoryDto.CategoryResponse;
import com.retail.catalog.entity.Category;
import com.retail.catalog.exception.ApiException;
import com.retail.catalog.repository.CategoryRepository;
import com.retail.catalog.repository.ProductRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CategoryService {

    static final String NOT_FOUND = "CATEGORY_NOT_FOUND";

    @Inject
    CategoryRepository categories;

    @Inject
    ProductRepository products;

    public List<CategoryResponse> listAll() {
        return categories.listAllSorted().stream().map(CategoryResponse::from).toList();
    }

    public CategoryResponse get(String rawId) {
        return CategoryResponse.from(require(rawId));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByName(name)) {
            throw duplicate();
        }
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName(name);
        try {
            categories.persist(category);
            categories.flush();
        } catch (PersistenceException e) {
            ServiceSupport.rethrowIfNotConstraint(e);
            throw duplicate();
        }
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse update(String rawId, CategoryRequest request) {
        Category category = require(rawId);
        String name = request.name().trim();
        if (categories.existsByNameExcludingId(name, category.getId())) {
            throw duplicate();
        }
        category.setName(name);
        try {
            categories.flush();
        } catch (PersistenceException e) {
            ServiceSupport.rethrowIfNotConstraint(e);
            throw duplicate();
        }
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(String rawId) {
        Category category = require(rawId);
        if (products.countByCategory(category.getId()) > 0) {
            throw inUse();
        }
        try {
            categories.delete(category);
            categories.flush();
        } catch (PersistenceException e) {
            ServiceSupport.rethrowIfNotConstraint(e);
            throw inUse();
        }
    }

    /** Used by ProductService; returns 404 CATEGORY_NOT_FOUND for unknown ids. */
    Category requireById(UUID id) {
        return categories.findByIdOptional(id)
                .orElseThrow(() -> ApiException.notFound(NOT_FOUND, "Category not found"));
    }

    private Category require(String rawId) {
        UUID id = ServiceSupport.parseIdOrNotFound(rawId, NOT_FOUND, "Category not found");
        return requireById(id);
    }

    private static ApiException duplicate() {
        return ApiException.conflict("CATEGORY_EXISTS", "Category name already exists");
    }

    private static ApiException inUse() {
        return ApiException.conflict("CATEGORY_IN_USE", "Category is referenced by products");
    }
}
