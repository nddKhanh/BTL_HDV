package com.retail.ai.service;

import com.retail.ai.dto.KnowledgeDocumentRequest;
import com.retail.ai.entity.KnowledgeDocument;
import com.retail.ai.exception.ApiProblem;
import com.retail.ai.repository.KnowledgeDocumentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class KnowledgeService {
    @Inject KnowledgeDocumentRepository documents;

    @Transactional
    public boolean saveFaq(KnowledgeDocumentRequest request) {
        if (request == null) throw new ApiProblem(422, "INVALID_DOCUMENT", "Missing request body");
        String sourceId = clean(request.sourceId());
        String title = clean(request.title());
        String content = clean(request.content());
        if (sourceId.isEmpty() || sourceId.length() > 120 || title.isEmpty() || title.length() > 240
                || content.isEmpty() || content.length() > 10_000) {
            throw new ApiProblem(422, "INVALID_DOCUMENT", "sourceId (1-120), title (1-240), content (1-10000) are required");
        }
        if (content.toLowerCase(java.util.Locale.ROOT).contains("<script")) {
            throw new ApiProblem(422, "INVALID_DOCUMENT", "Only plain text knowledge is accepted");
        }
        KnowledgeDocument doc = documents.bySource("FAQ", sourceId);
        boolean created = doc == null;
        if (created) {
            doc = new KnowledgeDocument();
            doc.id = UUID.randomUUID();
            doc.sourceType = "FAQ";
            doc.sourceId = sourceId;
        }
        doc.title = title;
        doc.content = content;
        doc.updatedAt = Instant.now();
        if (created) documents.persist(doc);
        return created;
    }

    private String clean(String text) { return text == null ? "" : text.trim(); }
}
