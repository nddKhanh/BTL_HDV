package com.retail.ai.repository;

import com.retail.ai.entity.KnowledgeDocument;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class KnowledgeDocumentRepository implements PanacheRepositoryBase<KnowledgeDocument, UUID> {
    public KnowledgeDocument bySource(String type, String id) {
        return find("sourceType = ?1 and sourceId = ?2", type, id).firstResult();
    }
}
