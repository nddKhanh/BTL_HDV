package com.retail.ai.service;

import com.retail.ai.client.CatalogAiClient;
import com.retail.ai.dto.ProductFeedResponse;
import com.retail.ai.dto.ProductFeedResponse.ProductKnowledgeDto;
import com.retail.ai.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductSyncServiceTest {

    private CatalogAiClient catalog;
    private KnowledgeDocumentRepository documents;
    private ProductSyncService service;

    @BeforeEach
    void setUp() {
        catalog = mock(CatalogAiClient.class);
        documents = mock(KnowledgeDocumentRepository.class);
        service = new ProductSyncService();
        service.catalog = catalog;
        service.documents = documents;
        service.internalKey = "test-internal-key";
    }

    @Test
    void fetchAll_singlePageSuccess() {
        var p1 = new ProductKnowledgeDto("1111", "TS-01", "Áo thun", "Mô tả 1", "Thời trang");
        var p2 = new ProductKnowledgeDto("2222", "BP-01", "Balo", "Mô tả 2", "Phụ kiện");

        when(catalog.products(eq("test-internal-key"), eq(0), eq(50)))
                .thenReturn(new ProductFeedResponse(List.of(p1, p2), null));

        Map<String, ProductKnowledgeDto> map = service.fetchAll();
        assertEquals(2, map.size());
        assertTrue(map.containsKey("1111"));
        assertTrue(map.containsKey("2222"));
    }

    @Test
    void fetchAll_multiPageSuccess() {
        var p1 = new ProductKnowledgeDto("1111", "TS-01", "Áo thun", "Mô tả 1", "Thời trang");
        var p2 = new ProductKnowledgeDto("2222", "BP-01", "Balo", "Mô tả 2", "Phụ kiện");

        when(catalog.products(eq("test-internal-key"), eq(0), eq(50)))
                .thenReturn(new ProductFeedResponse(List.of(p1), 50));
        when(catalog.products(eq("test-internal-key"), eq(50), eq(50)))
                .thenReturn(new ProductFeedResponse(List.of(p2), null));

        Map<String, ProductKnowledgeDto> map = service.fetchAll();
        assertEquals(2, map.size());
        assertEquals("Áo thun", map.get("1111").name());
        assertEquals("Balo", map.get("2222").name());
    }

    @Test
    void fetchAll_detectsInvalidNextOffsetLoop() {
        var p1 = new ProductKnowledgeDto("1111", "TS-01", "Áo thun", "Mô tả 1", "Thời trang");

        when(catalog.products(eq("test-internal-key"), eq(0), eq(50)))
                .thenReturn(new ProductFeedResponse(List.of(p1), 0));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.fetchAll());
        assertTrue(ex.getMessage().contains("C5 invalid nextOffset"));
    }

    @Test
    void fetchAll_detectsNullEnvelope() {
        when(catalog.products(anyString(), anyInt(), anyInt())).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.fetchAll());
        assertTrue(ex.getMessage().contains("empty envelope"));
    }

    @Test
    void fetchAll_detectsDuplicateProduct() {
        var p1 = new ProductKnowledgeDto("1111", "TS-01", "Áo thun", "Mô tả 1", "Thời trang");
        var p1dup = new ProductKnowledgeDto("1111", "TS-01-dup", "Áo thun 2", "Mô tả dup", "Thời trang");

        when(catalog.products(anyString(), anyInt(), anyInt()))
                .thenReturn(new ProductFeedResponse(List.of(p1, p1dup), null));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.fetchAll());
        assertTrue(ex.getMessage().contains("duplicate product"));
    }
}
