package com.retail.ai.service;

import com.retail.ai.dto.KnowledgeDocumentRequest;
import com.retail.ai.entity.KnowledgeDocument;
import com.retail.ai.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgeServiceTest {

    private KnowledgeDocumentRepository documents;
    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        documents = mock(KnowledgeDocumentRepository.class);
        service = new KnowledgeService();
        service.documents = documents;
    }

    @Test
    void saveFaq_createsNewDocumentWhenNotFound() {
        when(documents.bySource("FAQ", "return-policy")).thenReturn(null);

        KnowledgeDocumentRequest request = new KnowledgeDocumentRequest(
                "return-policy",
                "Chính sách đổi trả",
                "Cho phép đổi trả trong 7 ngày."
        );

        boolean created = service.saveFaq(request);
        assertTrue(created);

        ArgumentCaptor<KnowledgeDocument> captor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(documents).persist(captor.capture());

        KnowledgeDocument saved = captor.getValue();
        assertNotNull(saved.id);
        assertEquals("FAQ", saved.sourceType);
        assertEquals("return-policy", saved.sourceId);
        assertEquals("Chính sách đổi trả", saved.title);
        assertEquals("Cho phép đổi trả trong 7 ngày.", saved.content);
        assertNotNull(saved.updatedAt);
    }

    @Test
    void saveFaq_updatesExistingDocumentWhenFound() {
        KnowledgeDocument existing = new KnowledgeDocument();
        existing.id = UUID.randomUUID();
        existing.sourceType = "FAQ";
        existing.sourceId = "return-policy";
        existing.title = "Cũ";
        existing.content = "Nội dung cũ";

        when(documents.bySource("FAQ", "return-policy")).thenReturn(existing);

        KnowledgeDocumentRequest request = new KnowledgeDocumentRequest(
                "return-policy",
                "Tiêu đề mới",
                "Nội dung mới"
        );

        boolean created = service.saveFaq(request);
        assertFalse(created);

        assertEquals("Tiêu đề mới", existing.title);
        assertEquals("Nội dung mới", existing.content);
        verify(documents, never()).persist(any(KnowledgeDocument.class));
    }
}
