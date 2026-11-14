package com.retail.ai.service;

import com.retail.ai.client.CatalogAiClient;
import com.retail.ai.dto.ProductFeedResponse;
import com.retail.ai.dto.ProductFeedResponse.ProductKnowledgeDto;
import com.retail.ai.entity.KnowledgeDocument;
import com.retail.ai.repository.KnowledgeDocumentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ProductSyncService {
    @Inject @RestClient CatalogAiClient catalog;
    @ConfigProperty(name = "app.internal-key") String internalKey;
    @Inject KnowledgeDocumentRepository documents;

    // No DB writes until *all* C5 pages have been fetched correctly.
    public Map<String, ProductKnowledgeDto> fetchAll() {
        Map<String, ProductKnowledgeDto> result = new HashMap<>();
        int offset = 0;
        while (true) {
            ProductFeedResponse page = catalog.products(internalKey, offset, 50);
            if (page == null || page.items() == null) throw new IllegalStateException("C5 returned empty envelope");
            for (ProductKnowledgeDto item : page.items()) {
                if (item == null || item.id() == null || item.id().isBlank()
                        || item.name() == null || item.name().isBlank()) {
                    throw new IllegalStateException("C5 returned invalid product");
                }
                if (result.putIfAbsent(item.id(), item) != null) {
                    throw new IllegalStateException("C5 returned duplicate product: " + item.id());
                }
                if (result.size() > 1000) throw new IllegalStateException("MVP limit: 1000 products");
            }
            Integer next = page.nextOffset();
            if (next == null) break;
            if (next <= offset || next > 1000) throw new IllegalStateException("C5 invalid nextOffset");
            offset = next;
        }
        return result;
    }

    @Transactional
    public void sync(Map<String, ProductKnowledgeDto> products) {
        List<KnowledgeDocument> existing = documents.list("sourceType", "PRODUCT");
        for (KnowledgeDocument doc : existing) {
            if (!products.containsKey(doc.sourceId)) documents.delete(doc);
        }
        for (ProductKnowledgeDto item : products.values()) {
            KnowledgeDocument doc = documents.bySource("PRODUCT", item.id());
            boolean created = doc == null;
            if (created) {
                doc = new KnowledgeDocument();
                doc.id = UUID.randomUUID();
                doc.sourceType = "PRODUCT";
                doc.sourceId = item.id();
            }
            doc.title = item.name();
            doc.content = "Tên: " + item.name()
                    + "\nSKU: " + clean(item.sku())
                    + "\nDanh mục: " + clean(item.categoryName())
                    + "\nMô tả: " + clean(item.description());
            doc.updatedAt = Instant.now();
            if (created) documents.persist(doc);
        }
    }

    private String clean(String text) { return text == null ? "" : text.trim(); }
}
