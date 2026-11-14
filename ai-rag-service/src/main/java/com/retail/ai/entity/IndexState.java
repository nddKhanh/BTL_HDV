package com.retail.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "index_state")
public class IndexState {
    @Id
    public Short id;
    @Column(nullable = false)
    public String status;
    @Column(name = "document_count", nullable = false)
    public int documentCount;
    @Column(name = "chunk_count", nullable = false)
    public int chunkCount;
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
