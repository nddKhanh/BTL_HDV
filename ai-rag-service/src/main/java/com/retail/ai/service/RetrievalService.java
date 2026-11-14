package com.retail.ai.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.List;

@ApplicationScoped
public class RetrievalService {
    @Inject EmbeddingModel model;
    @Inject EmbeddingStore<TextSegment> store;
    @ConfigProperty(name = "app.rag.min-score", defaultValue = "0.65") double minScore;

    public List<EmbeddingMatch<TextSegment>> search(String question) {
        var queryVector = model.embed(question).content();
        var request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryVector)
                .maxResults(4)
                .minScore(minScore)
                .build();
        return store.search(request).matches();
    }
}
