package com.retail.catalog.service;

import com.retail.catalog.dto.KnowledgeFeedDto.KnowledgeFeedResponse;
import com.retail.catalog.dto.KnowledgeFeedDto.ProductKnowledgeDto;
import com.retail.catalog.exception.ApiException;
import com.retail.catalog.repository.ProductRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

/** C5: knowledge feed for AI RAG. Active products only, stable id ASC order, no price/stock. */
@ApplicationScoped
public class KnowledgeExportService {

    public static final int MAX_LIMIT = 100;

    @Inject
    ProductRepository products;

    public KnowledgeFeedResponse page(int offset, int limit) {
        if (offset < 0) {
            throw ApiException.unprocessable("VALIDATION_ERROR", "offset: must be >= 0");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw ApiException.unprocessable("VALIDATION_ERROR", "limit: must be between 1 and " + MAX_LIMIT);
        }
        // Fetch one extra row to know whether another page exists.
        List<Object[]> rows = products.getEntityManager().createQuery(
                        "select p.id, p.sku, p.name, p.description, c.name "
                                + "from Product p join p.category c "
                                + "where p.active = true order by p.id asc", Object[].class)
                .setFirstResult(offset)
                .setMaxResults(limit + 1)
                .getResultList();

        boolean hasMore = rows.size() > limit;
        List<ProductKnowledgeDto> items = rows.stream()
                .limit(limit)
                .map(r -> new ProductKnowledgeDto((UUID) r[0], (String) r[1], (String) r[2],
                        r[3] == null ? "" : (String) r[3], (String) r[4]))
                .toList();
        return new KnowledgeFeedResponse(items, hasMore ? offset + limit : null);
    }
}
