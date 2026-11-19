package com.retail.ai.service;

import com.retail.ai.dto.KnowledgeDocumentRequest;
import com.retail.ai.entity.KnowledgeDocument;
import com.retail.ai.exception.ApiProblem;
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

    @Test
    void saveFaq_throwsWhenRequestIsNull() {
        ApiProblem ex = assertThrows(ApiProblem.class, () -> service.saveFaq(null));
        assertEquals(422, ex.status);
        assertEquals("INVALID_DOCUMENT", ex.code);
    }

    @Test
    void saveFaq_throwsWhenSourceIdIsBlankOrTooLong() {
        ApiProblem ex1 = assertThrows(ApiProblem.class, () ->
                service.saveFaq(new KnowledgeDocumentRequest("   ", "Tiêu đề", "Nội dung")));
        assertEquals(422, ex1.status);

        String tooLong = "a".repeat(121);
        ApiProblem ex2 = assertThrows(ApiProblem.class, () ->
                service.saveFaq(new KnowledgeDocumentRequest(tooLong, "Tiêu đề", "Nội dung")));
        assertEquals(422, ex2.status);
    }

    @Test
    void saveFaq_throwsWhenTitleIsBlankOrTooLong() {
        ApiProblem ex1 = assertThrows(ApiProblem.class, () ->
                service.saveFaq(new KnowledgeDocumentRequest("id-1", "", "Nội dung")));
        assertEquals(422, ex1.status);

        String tooLong = "t".repeat(241);
        ApiProblem ex2 = assertThrows(ApiProblem.class, () ->
                service.saveFaq(new KnowledgeDocumentRequest("id-1", tooLong, "Nội dung")));
        assertEquals(422, ex2.status);
    }

    @Test
    void saveFaq_throwsWhenContentIsBlankOrTooLong() {
        ApiProblem ex1 = assertThrows(ApiProblem.class, () ->
                service.saveFaq(new KnowledgeDocumentRequest("id-1", "Tiêu đề", "   ")));
        assertEquals(422, ex1.status);

        String tooLong = "c".repeat(10_001);
        ApiProblem ex2 = assertThrows(ApiProblem.class, () ->
                service.saveFaq(new KnowledgeDocumentRequest("id-1", "Tiêu đề", tooLong)));
        assertEquals(422, ex2.status);
    }

    @Test
    void saveFaq_rejectsScriptTagInContent() {
        KnowledgeDocumentRequest request = new KnowledgeDocumentRequest(
                "script-id",
                "Hack",
                "Chính sách <script>alert('xss')</script>"
        );
        ApiProblem ex = assertThrows(ApiProblem.class, () -> service.saveFaq(request));
        assertEquals(422, ex.status);
        assertTrue(ex.getMessage().contains("Only plain text knowledge is accepted"));
    }
}
