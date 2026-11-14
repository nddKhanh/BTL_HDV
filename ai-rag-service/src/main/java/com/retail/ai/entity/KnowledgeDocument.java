package com.retail.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument {
    @Id
    public UUID id;

    @Column(name = "source_type", nullable = false, length = 16)
    public String sourceType;

    @Column(name = "source_id", nullable = false, length = 120)
    public String sourceId;

    @Column(name = "title", nullable = false, length = 240)
    public String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    public String content;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
