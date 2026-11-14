package com.retail.ai;

import com.retail.ai.entity.KnowledgeDocument;
import com.retail.ai.service.ChunkService;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ChunkServiceTest {
    private KnowledgeDocument document(String text) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.id = UUID.randomUUID();
        doc.sourceType = "FAQ";
        doc.sourceId = "policy";
        doc.title = "Chính sách";
        doc.content = text;
        return doc;
    }

    @Test
    void shortContentBecomesOneChunk() {
        var parts = new ChunkService().split(document("Hỗ trợ đổi trả trong 7 ngày."));
        assertEquals(1, parts.size());
        assertEquals("FAQ", parts.get(0).metadata().getString("sourceType"));
        assertEquals("policy", parts.get(0).metadata().getString("sourceId"));
        assertEquals(0, parts.get(0).metadata().getInteger("chunkIndex"));
    }

    @Test
    void longContentSplitsAndEmptyIsSkipped() {
        var service = new ChunkService();
        assertTrue(service.split(document("  ")).isEmpty());
        var parts = service.split(document("cotton ".repeat(300)));
        assertTrue(parts.size() >= 3);
        assertTrue(parts.stream().allMatch(part -> part.text().length() <= 700));
    }
}
