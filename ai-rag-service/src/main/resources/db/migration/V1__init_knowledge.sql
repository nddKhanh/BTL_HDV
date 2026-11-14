CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_documents (
    id UUID PRIMARY KEY,
    source_type VARCHAR(16) NOT NULL CHECK (source_type IN ('FAQ', 'PRODUCT')),
    source_id VARCHAR(120) NOT NULL,
    title VARCHAR(240) NOT NULL,
    content TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_type, source_id)
);

CREATE INDEX idx_knowledge_documents_source
    ON knowledge_documents (source_type, source_id);

-- MVP: persisted state lets the service know if a previous full rebuild failed.
CREATE TABLE index_state (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    status VARCHAR(20) NOT NULL CHECK (status IN ('EMPTY','REINDEXING','READY','FAILED')),
    document_count INTEGER NOT NULL DEFAULT 0,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO index_state (id, status) VALUES (1, 'EMPTY');

-- The LangChain4j PGVector extension owns the embeddings table.
-- Do not create an embeddings table in Flyway.
