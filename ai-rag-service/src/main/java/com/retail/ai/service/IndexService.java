package com.retail.ai.service;

import com.retail.ai.dto.IndexResponse;
import com.retail.ai.entity.KnowledgeDocument;
import com.retail.ai.exception.ApiProblem;
import com.retail.ai.repository.KnowledgeDocumentRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class IndexService {
    private static final Logger LOG = Logger.getLogger(IndexService.class);
    @Inject ProductSyncService products;
    @Inject KnowledgeDocumentRepository documents;
    @Inject ChunkService chunks;
    @Inject IndexStateService state;
    @Inject EmbeddingModel embeddingModel;
    @Inject EmbeddingStore<TextSegment> store;
    @Inject DataSource dataSource;

    // MVP: synchronous full reindex, 1 AI instance, no distributed locks.
    public synchronized IndexResponse reindex() {
        state.set("REINDEXING", 0, 0);
        try {
            var feed = products.fetchAll();
            products.sync(feed);
            List<KnowledgeDocument> all = documents.listAll();
            List<TextSegment> segments = new ArrayList<>();
            for (KnowledgeDocument doc : all) segments.addAll(chunks.split(doc));

            // Calculate embeddings BEFORE touching the old vector index.
            List<Embedding> vectors = new ArrayList<>();
            for (int i = 0; i < segments.size(); i += 20) {
                List<TextSegment> batch = segments.subList(i, Math.min(i + 20, segments.size()));
                List<Embedding> embedded = embeddingModel.embedAll(batch).content();
                if (embedded.size() != batch.size()
                        || embedded.stream().anyMatch(v -> v.vector().length != 1536)) {
                    throw new IllegalStateException("Embedding dimension/count mismatch");
                }
                vectors.addAll(embedded);
            }

            // The PGVector extension is configured to own this explicit table name.
            // Whole-table DELETE avoids relying on unsupported EmbeddingStore.removeAll().
            try (Connection conn = dataSource.getConnection(); Statement sql = conn.createStatement()) {
                sql.executeUpdate("DELETE FROM embeddings");
            }
            for (int i = 0; i < vectors.size(); i += 20) {
                int end = Math.min(i + 20, vectors.size());
                store.addAll(vectors.subList(i, end), segments.subList(i, end));
            }

            state.set("READY", all.size(), segments.size());
            return new IndexResponse("READY", all.size(), segments.size());
        } catch (Exception e) {
            LOG.error("Reindex failed (details remain in server logs)", e);
            state.set("FAILED", 0, 0);
            throw new ApiProblem(503, "REINDEX_FAILED", "Cannot rebuild the knowledge index");
        }
    }
}
