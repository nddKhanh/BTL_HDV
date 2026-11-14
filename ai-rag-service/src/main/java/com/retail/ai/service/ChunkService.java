package com.retail.ai.service;

import com.retail.ai.entity.KnowledgeDocument;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ChunkService {
    private static final int CHUNK_SIZE = 700;
    private static final int OVERLAP = 100;

    public List<TextSegment> split(KnowledgeDocument doc) {
        String content = doc.content == null ? "" : doc.content.strip();
        if (content.isEmpty()) return List.of();
        List<TextSegment> result = new ArrayList<>();
        int start = 0;
        int i = 0;
        while (start < content.length()) {
            int end = Math.min(start + CHUNK_SIZE, content.length());
            if (end < content.length()) {
                int boundary = content.lastIndexOf(' ', end);
                if (boundary > start + 500) end = boundary;
            }
            String part = content.substring(start, end).strip();
            if (!part.isEmpty()) {
                Metadata meta = Metadata.from(Map.of(
                    "sourceType", doc.sourceType,
                    "sourceId", doc.sourceId,
                    "title", doc.title,
                    "documentId", doc.id.toString(),
                    "chunkIndex", i++
                ));
                result.add(TextSegment.from(part, meta));
            }
            if (end == content.length()) break;
            start = Math.max(start + 1, end - OVERLAP);
        }
        return result;
    }
}
