package com.retail.ai.service;

import com.retail.ai.entity.IndexState;
import com.retail.ai.repository.IndexStateRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;

@ApplicationScoped
public class IndexStateService {
    @Inject IndexStateRepository repo;

    @Transactional
    public void set(String status, int documentCount, int chunkCount) {
        IndexState state = repo.current();
        state.status = status;
        state.documentCount = documentCount;
        state.chunkCount = chunkCount;
        state.updatedAt = Instant.now();
    }

    public boolean ready() {
        return "READY".equals(repo.current().status);
    }
}
