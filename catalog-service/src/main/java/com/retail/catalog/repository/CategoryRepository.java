package com.retail.catalog.repository;

import com.retail.catalog.entity.Category;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CategoryRepository implements PanacheRepositoryBase<Category, UUID> {

    public List<Category> listAllSorted() {
        return listAll(Sort.by("name").and("id"));
    }

    public boolean existsByName(String name) {
        return count("name", name) > 0;
    }

    public boolean existsByNameExcludingId(String name, UUID id) {
        return count("name = ?1 and id <> ?2", name, id) > 0;
    }
}
