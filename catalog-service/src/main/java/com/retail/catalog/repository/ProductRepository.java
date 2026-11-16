package com.retail.catalog.repository;

import com.retail.catalog.entity.Product;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ProductRepository implements PanacheRepositoryBase<Product, UUID> {

    public Optional<Product> findActiveById(UUID id) {
        return find("id = ?1 and active = true", id).firstResultOptional();
    }

    /** SELECT ... FOR UPDATE — same row lock used by reserve/release (C2/C4). */
    public Optional<Product> findByIdForUpdate(UUID id) {
        return findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE);
    }

    public List<Product> listByIds(Collection<UUID> ids) {
        return list("id in ?1", ids);
    }

    public boolean existsBySku(String sku) {
        return count("sku", sku) > 0;
    }

    public long countByCategory(UUID categoryId) {
        return count("category.id", categoryId);
    }

    /**
     * Public search: active only, optional case-insensitive name filter and category filter,
     * stable ordering name ASC, id ASC. User input is always bound as a parameter.
     */
    public PanacheQuery<Product> searchActive(String query, UUID categoryId, int page, int size) {
        StringBuilder jpql = new StringBuilder("active = true");
        Parameters params = new Parameters();
        if (categoryId != null) {
            jpql.append(" and category.id = :categoryId");
            params.and("categoryId", categoryId);
        }
        if (query != null && !query.isBlank()) {
            jpql.append(" and lower(name) like lower(:pattern) escape '!'");
            params.and("pattern", "%" + escapeLike(query.trim()) + "%");
        }
        return find(jpql.toString(), Sort.by("name").and("id"), params).page(Page.of(page, size));
    }

    private static String escapeLike(String raw) {
        return raw.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
